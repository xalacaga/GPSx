#![forbid(unsafe_code)]

//! Portable session state and failure types.

use core::fmt;

/// Observable lifecycle states for one Remote Play session.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SessionState {
    Idle,
    Discovering,
    Registering,
    Connecting,
    Streaming,
    Reconnecting,
    Stopping,
    Stopped,
    Failed,
}

impl SessionState {
    /// Returns whether a transition is valid at the portable lifecycle layer.
    #[must_use]
    pub const fn can_transition_to(self, next: Self) -> bool {
        use SessionState::{
            Connecting, Discovering, Failed, Idle, Reconnecting, Registering, Stopped, Stopping,
            Streaming,
        };

        matches!(
            (self, next),
            (Idle, Discovering | Registering | Connecting | Stopped)
                | (
                    Discovering,
                    Idle | Registering | Connecting | Failed | Stopping
                )
                | (Registering, Idle | Connecting | Failed | Stopping)
                | (Connecting, Streaming | Reconnecting | Failed | Stopping)
                | (Streaming, Reconnecting | Failed | Stopping)
                | (Reconnecting, Connecting | Streaming | Failed | Stopping)
                | (Stopping, Stopped | Failed)
                | (Stopped, Idle)
                | (Failed, Idle | Stopping)
        )
    }
}

/// Small state machine that rejects implicit or impossible lifecycle jumps.
#[derive(Debug, Eq, PartialEq)]
pub struct SessionStateMachine {
    state: SessionState,
}

impl Default for SessionStateMachine {
    fn default() -> Self {
        Self {
            state: SessionState::Idle,
        }
    }
}

impl SessionStateMachine {
    #[must_use]
    pub const fn state(&self) -> SessionState {
        self.state
    }

    pub fn transition(&mut self, next: SessionState) -> Result<(), InvalidTransition> {
        if !self.state.can_transition_to(next) {
            return Err(InvalidTransition {
                from: self.state,
                to: next,
            });
        }
        self.state = next;
        Ok(())
    }

    /// Idempotent stop request, valid from any state. An idle session has nothing to
    /// tear down and stops immediately; repeated requests change nothing.
    pub fn request_stop(&mut self) -> StopOutcome {
        match self.state {
            SessionState::Stopping => StopOutcome::AlreadyStopping,
            SessionState::Stopped => StopOutcome::AlreadyStopped,
            SessionState::Idle => {
                self.state = SessionState::Stopped;
                StopOutcome::Stopped
            }
            SessionState::Discovering
            | SessionState::Registering
            | SessionState::Connecting
            | SessionState::Streaming
            | SessionState::Reconnecting
            | SessionState::Failed => {
                self.state = SessionState::Stopping;
                StopOutcome::Stopping
            }
        }
    }
}

/// Result of [`SessionStateMachine::request_stop`].
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum StopOutcome {
    /// Teardown started; the owner must later transition to `Stopped`.
    Stopping,
    /// Nothing was running; the session is stopped.
    Stopped,
    AlreadyStopping,
    AlreadyStopped,
}

/// An invalid session lifecycle transition.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct InvalidTransition {
    pub from: SessionState,
    pub to: SessionState,
}

impl fmt::Display for InvalidTransition {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            formatter,
            "invalid session transition: {:?} -> {:?}",
            self.from, self.to
        )
    }
}

impl std::error::Error for InvalidTransition {}

#[cfg(test)]
mod tests {
    use super::{SessionState, SessionStateMachine, StopOutcome};

    use SessionState::{
        Connecting, Discovering, Failed, Idle, Reconnecting, Registering, Stopped, Stopping,
        Streaming,
    };

    const ALL: [SessionState; 9] = [
        Idle,
        Discovering,
        Registering,
        Connecting,
        Streaming,
        Reconnecting,
        Stopping,
        Stopped,
        Failed,
    ];

    /// Reference matrix, written independently of `can_transition_to`.
    const ALLOWED: &[(SessionState, SessionState)] = &[
        (Idle, Discovering),
        (Idle, Registering),
        (Idle, Connecting),
        (Idle, Stopped),
        (Discovering, Idle),
        (Discovering, Registering),
        (Discovering, Connecting),
        (Discovering, Failed),
        (Discovering, Stopping),
        (Registering, Idle),
        (Registering, Connecting),
        (Registering, Failed),
        (Registering, Stopping),
        (Connecting, Streaming),
        (Connecting, Reconnecting),
        (Connecting, Failed),
        (Connecting, Stopping),
        (Streaming, Reconnecting),
        (Streaming, Failed),
        (Streaming, Stopping),
        (Reconnecting, Connecting),
        (Reconnecting, Streaming),
        (Reconnecting, Failed),
        (Reconnecting, Stopping),
        (Stopping, Stopped),
        (Stopping, Failed),
        (Stopped, Idle),
        (Failed, Idle),
        (Failed, Stopping),
    ];

    #[test]
    fn transition_table_matches_reference_exhaustively() {
        for from in ALL {
            for to in ALL {
                assert_eq!(
                    from.can_transition_to(to),
                    ALLOWED.contains(&(from, to)),
                    "{from:?} -> {to:?}"
                );
            }
        }
    }

    #[test]
    fn stop_request_is_idempotent_from_every_state() {
        for start in ALL {
            let mut machine = SessionStateMachine { state: start };
            let first = machine.request_stop();
            assert!(
                matches!(machine.state(), Stopping | Stopped),
                "{start:?} -> {first:?}"
            );
            let settled = machine.state();
            let second = machine.request_stop();
            assert!(matches!(
                second,
                StopOutcome::AlreadyStopping | StopOutcome::AlreadyStopped
            ));
            assert_eq!(machine.state(), settled, "repeat stop changed {start:?}");
        }
    }

    #[test]
    fn accepts_nominal_stream_lifecycle() {
        let mut machine = SessionStateMachine::default();
        for state in [
            SessionState::Connecting,
            SessionState::Streaming,
            SessionState::Stopping,
            SessionState::Stopped,
        ] {
            assert_eq!(machine.transition(state), Ok(()));
        }
    }

    #[test]
    fn rejects_streaming_without_connection() {
        let mut machine = SessionStateMachine::default();
        let error = machine
            .transition(SessionState::Streaming)
            .expect_err("idle sessions cannot begin streaming directly");
        assert_eq!(error.from, SessionState::Idle);
        assert_eq!(machine.state(), SessionState::Idle);
    }
}
