// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
#![forbid(unsafe_code)]

use std::env;
use std::process::ExitCode;

use gpsx_protocol::{ConsoleGeneration, DiscoveryState};
use gpsx_transport::{
    DEFAULT_RESPONSE_WINDOW, DiscoveryError, DiscoveryOperation, DiscoveryReport,
    discover_broadcast,
};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum Mode {
    Help,
    Live,
}

fn main() -> ExitCode {
    let arguments = env::args().skip(1).collect::<Vec<_>>();
    match parse_mode(&arguments) {
        Some(Mode::Help) => {
            print_help();
            ExitCode::SUCCESS
        }
        Some(Mode::Live) => run_live(),
        None => {
            eprintln!("Aucun paquet n'a été envoyé. Utilisez --help ou confirmez avec --live.");
            ExitCode::from(2)
        }
    }
}

fn parse_mode(arguments: &[String]) -> Option<Mode> {
    match arguments {
        [argument] if argument == "--help" || argument == "-h" => Some(Mode::Help),
        [argument] if argument == "--live" => Some(Mode::Live),
        _ => None,
    }
}

fn run_live() -> ExitCode {
    println!("Recherche locale PS5 puis PS4 (aucun réveil, aucune authentification)…");
    for generation in [ConsoleGeneration::Ps5, ConsoleGeneration::Ps4] {
        match discover_broadcast(generation, DEFAULT_RESPONSE_WINDOW) {
            Ok(report) => print_report(generation, &report),
            Err(error) => {
                eprintln!(
                    "Échec de la découverte {} : {}.",
                    generation_label(generation),
                    error_label(error)
                );
                return ExitCode::FAILURE;
            }
        }
    }
    ExitCode::SUCCESS
}

fn print_report(generation: ConsoleGeneration, report: &DiscoveryReport) {
    println!(
        "{} : port local {}, {} console(s), {} datagramme(s) rejeté(s), {} erreur(s) ICMP, {} port(s) source inattendu(s), {} doublon(s), {} réponse(s) hors capacité.",
        generation_label(generation),
        report.local_port(),
        report.consoles().len(),
        report.rejected_datagrams(),
        report.icmp_errors(),
        report.unexpected_source_ports(),
        report.duplicate_responses(),
        report.capacity_drops(),
    );
    for (index, console) in report.consoles().iter().enumerate() {
        println!(
            "  Console {} : état={}, statut={}, port de requête={}",
            index + 1,
            state_label(console.state()),
            console.status_code(),
            console
                .host_request_port()
                .map_or_else(|| "indisponible".to_owned(), |port| port.to_string()),
        );
    }
}

const fn generation_label(generation: ConsoleGeneration) -> &'static str {
    match generation {
        ConsoleGeneration::Ps4 => "PS4",
        ConsoleGeneration::Ps5 => "PS5",
    }
}

const fn state_label(state: DiscoveryState) -> &'static str {
    match state {
        DiscoveryState::Ready => "prête",
        DiscoveryState::Standby => "en veille",
        DiscoveryState::Unknown => "inconnu",
    }
}

const fn error_label(error: DiscoveryError) -> &'static str {
    match error {
        DiscoveryError::InvalidResponseWindow => "fenêtre de réponse invalide",
        DiscoveryError::Io { operation, .. } => match operation {
            DiscoveryOperation::Bind => "ouverture de la socket impossible",
            DiscoveryOperation::InspectLocalAddress => "lecture du port local impossible",
            DiscoveryOperation::ConfigureBroadcast => "activation de la diffusion impossible",
            DiscoveryOperation::ConfigureTimeout => "configuration du délai impossible",
            DiscoveryOperation::Send => "envoi de la requête impossible",
            DiscoveryOperation::Receive => "réception de la réponse impossible",
        },
    }
}

fn print_help() {
    println!(
        "GPSx — découverte locale PS4/PS5\n\n\
         Usage :\n  gpsx-discover --help\n  gpsx-discover --live\n\n\
         --live envoie une requête UDP SRCH bornée vers les ports officiels de\n\
         découverte PS5 et PS4. Aucun réveil, identifiant, secret, enregistrement\n\
         ou démarrage de session n'est effectué."
    );
}

#[cfg(test)]
mod tests {
    use gpsx_protocol::{ConsoleGeneration, DiscoveryState};
    use gpsx_transport::{DiscoveryError, DiscoveryOperation};

    use super::{Mode, error_label, generation_label, parse_mode, state_label};

    fn arguments(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| (*value).to_owned()).collect()
    }

    #[test]
    fn live_network_mode_requires_one_exact_argument() {
        assert_eq!(parse_mode(&arguments(&["--live"])), Some(Mode::Live));
        for invalid in [
            arguments(&[]),
            arguments(&["live"]),
            arguments(&["--live", "extra"]),
            arguments(&["--unknown"]),
        ] {
            assert_eq!(parse_mode(&invalid), None);
        }
    }

    #[test]
    fn help_mode_is_explicit_and_separate_from_live_mode() {
        assert_eq!(parse_mode(&arguments(&["--help"])), Some(Mode::Help));
        assert_eq!(parse_mode(&arguments(&["-h"])), Some(Mode::Help));
    }

    #[test]
    fn every_user_facing_runtime_label_is_french() {
        assert_eq!(generation_label(ConsoleGeneration::Ps5), "PS5");
        assert_eq!(state_label(DiscoveryState::Ready), "prête");
        assert_eq!(state_label(DiscoveryState::Standby), "en veille");
        assert_eq!(state_label(DiscoveryState::Unknown), "inconnu");
        assert_eq!(
            error_label(DiscoveryError::InvalidResponseWindow),
            "fenêtre de réponse invalide"
        );
        assert_eq!(
            error_label(DiscoveryError::Io {
                operation: DiscoveryOperation::Receive,
                kind: std::io::ErrorKind::PermissionDenied,
            }),
            "réception de la réponse impossible"
        );
    }
}
