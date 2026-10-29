// SPDX-License-Identifier: AGPL-3.0-only

//! JNI adapter for the portable `gpsx-input` state machine.
//!
//! JNI primitives are copied by value. No JNI handle is dereferenced or retained.

use gpsx_input::{
    Axis, Button, ControllerId, ControllerSet, ControllerState, InputTransitionQueue, StickValue,
};
use std::ffi::c_void;
use std::io::{Read, Write};
use std::net::{IpAddr, Ipv4Addr, SocketAddr, SocketAddrV4, TcpStream};
use std::num::NonZeroUsize;
use std::sync::{Mutex, OnceLock};
use std::time::Duration;

use gpsx_protocol::ConsoleGeneration;
use gpsx_transport::discover_broadcast;

const EDGE_QUEUE_DEPTH: usize = 128;
const ANDROID_RELAY_PORT: u16 = 49_302;
const ANDROID_RELAY_REQUEST: &[u8] = b"GPSX_DISCOVER_PS5_V1\n";

struct Bridge {
    controllers: ControllerSet,
    edges: InputTransitionQueue,
    merged: ControllerState,
    sequence: u64,
}

impl Default for Bridge {
    fn default() -> Self {
        Self {
            controllers: ControllerSet::default(),
            edges: InputTransitionQueue::new(
                NonZeroUsize::new(EDGE_QUEUE_DEPTH).expect("edge queue depth is non-zero"),
            ),
            merged: ControllerState::default(),
            sequence: 0,
        }
    }
}

static BRIDGE: OnceLock<Mutex<Bridge>> = OnceLock::new();

fn bridge() -> &'static Mutex<Bridge> {
    BRIDGE.get_or_init(|| Mutex::new(Bridge::default()))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_gpsx_tv_remoteplay_GpsxInputBridge_nativeUpdate(
    _env: *mut c_void,
    _class: *mut c_void,
    device_id: i64,
    buttons: i32,
    l2: i32,
    r2: i32,
    left_x: i32,
    left_y: i32,
    right_x: i32,
    right_y: i32,
    event_time_ns: i64,
) -> i64 {
    let mut state = ControllerState::default();
    for button in Button::ALL {
        state.set_button(button, (buttons as u32) & button.mask() != 0);
    }
    state.left_trigger = l2.clamp(0, u8::MAX.into()) as u8;
    state.right_trigger = r2.clamp(0, u8::MAX.into()) as u8;
    state.set_stick(Axis::LeftX, StickValue(clamp_axis(left_x)));
    state.set_stick(Axis::LeftY, StickValue(clamp_axis(left_y)));
    state.set_stick(Axis::RightX, StickValue(clamp_axis(right_x)));
    state.set_stick(Axis::RightY, StickValue(clamp_axis(right_y)));

    let Ok(mut adapter) = bridge().lock() else {
        return -1;
    };
    adapter.sequence = adapter.sequence.wrapping_add(1);
    let sequence = adapter.sequence;
    let id = ControllerId((device_id as u32 as u64).saturating_add(1));
    let Ok(update) =
        adapter
            .controllers
            .update_merged(id, state, event_time_ns.max(0) as u64, sequence)
    else {
        return -1;
    };
    if adapter.edges.push_batch(&update.transitions).is_err() {
        return -1;
    }
    while adapter.edges.pop().is_some() {}
    adapter.merged = update.state;

    i64::from(adapter.merged.buttons())
        | (i64::from(adapter.merged.left_trigger) << 32)
        | (i64::from(adapter.merged.right_trigger) << 40)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_gpsx_tv_remoteplay_GpsxInputBridge_nativeAxis(
    _env: *mut c_void,
    _class: *mut c_void,
    axis: i32,
) -> i32 {
    let Ok(adapter) = bridge().lock() else {
        return 0;
    };
    let axis = match axis {
        0 => Axis::LeftX,
        1 => Axis::LeftY,
        2 => Axis::RightX,
        3 => Axis::RightY,
        _ => return 0,
    };
    i32::from(adapter.merged.stick(axis).0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_gpsx_tv_remoteplay_GpsxInputBridge_nativeOverflowCount(
    _env: *mut c_void,
    _class: *mut c_void,
) -> i64 {
    bridge()
        .lock()
        .map_or(-1, |adapter| adapter.edges.overflows() as i64)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_gpsx_tv_remoteplay_GpsxInputBridge_nativeReset(
    _env: *mut c_void,
    _class: *mut c_void,
) {
    if let Ok(mut adapter) = bridge().lock() {
        *adapter = Bridge::default();
    }
}

/// Returns the first PS5 IPv4 address as a network-order positive integer.
/// Zero means no response and -1 means a classified discovery failure.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_gpsx_tv_remoteplay_GpsxDiscoveryBridge_nativeDiscoverPs5(
    _env: *mut c_void,
    _class: *mut c_void,
    timeout_ms: i32,
) -> i64 {
    if !(1..=5_000).contains(&timeout_ms) {
        return -1;
    }
    let Ok(report) = discover_broadcast(
        ConsoleGeneration::Ps5,
        Duration::from_millis(timeout_ms as u64),
    ) else {
        return -1;
    };
    report
        .consoles()
        .iter()
        .find_map(|console| match console.peer().ip() {
            IpAddr::V4(address) => Some(i64::from(u32::from(address))),
            IpAddr::V6(_) => None,
        })
        .unwrap_or(0)
}

/// Uses the emulator's documented `10.0.2.2` host alias to reach the loopback-only
/// GPSx discovery bridge. Returns -2 when the host bridge is unavailable.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_gpsx_tv_remoteplay_GpsxDiscoveryBridge_nativeDiscoverPs5ViaEmulatorHost(
    _env: *mut c_void,
    _class: *mut c_void,
    timeout_ms: i32,
) -> i64 {
    if !(1..=5_000).contains(&timeout_ms) {
        return -1;
    }
    let address = SocketAddr::V4(SocketAddrV4::new(
        Ipv4Addr::new(10, 0, 2, 2),
        ANDROID_RELAY_PORT,
    ));
    let Ok(mut stream) = TcpStream::connect_timeout(&address, Duration::from_millis(500)) else {
        return -2;
    };
    let response_timeout = Duration::from_millis(timeout_ms as u64 + 1_000);
    if stream
        .set_read_timeout(Some(response_timeout))
        .and_then(|()| stream.set_write_timeout(Some(Duration::from_millis(500))))
        .and_then(|()| stream.write_all(ANDROID_RELAY_REQUEST))
        .is_err()
    {
        return -2;
    }
    let mut response = [0_u8; 4];
    if stream.read_exact(&mut response).is_err() {
        return -2;
    }
    i64::from(u32::from_be_bytes(response))
}

fn clamp_axis(value: i32) -> i16 {
    value.clamp(i32::from(i16::MIN), i32::from(i16::MAX)) as i16
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn axis_clamping_is_total() {
        assert_eq!(clamp_axis(i32::MIN), i16::MIN);
        assert_eq!(clamp_axis(i32::MAX), i16::MAX);
        assert_eq!(clamp_axis(42), 42);
    }

    #[test]
    fn ipv4_network_order_round_trips_for_jni_packing() {
        let address = std::net::Ipv4Addr::new(192, 168, 1, 42);
        let packed = u32::from(address);
        assert_eq!(packed, 0xc0a8_012a);
        assert_eq!(std::net::Ipv4Addr::from(packed), address);
    }
}
