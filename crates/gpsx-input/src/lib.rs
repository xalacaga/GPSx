// SPDX-License-Identifier: AGPL-3.0-only
#![forbid(unsafe_code)]

//! Platform-neutral, multi-controller input model.
//!
//! Button edges and trigger engage/release crossings are discrete transitions that are
//! never coalesced or dropped. Stick positions and intermediate trigger values are
//! continuous state: the latest value wins.

use std::collections::{BTreeMap, VecDeque};
use std::num::NonZeroUsize;

/// GPSx accepts at most two simultaneously active physical controllers.
pub const MAX_LOCAL_CONTROLLERS: usize = 2;

/// Controller identifier used for the single merged Remote Play player state.
pub const REMOTE_PLAYER: ControllerId = ControllerId(0);

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct ControllerId(pub u64);

/// Physical PlayStation controls.
///
/// The discriminant is GPSx's **internal** bit position. It is deliberately not the
/// Remote Play wire order; conversion to the wire belongs to the protocol layer once
/// ADR-0001 is accepted.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
#[repr(u8)]
pub enum Button {
    Cross,
    Circle,
    Square,
    Triangle,
    DpadUp,
    DpadDown,
    DpadLeft,
    DpadRight,
    LeftBumper,
    RightBumper,
    LeftStick,
    RightStick,
    Create,
    Options,
    PlayStation,
    Touchpad,
}

impl Button {
    pub const ALL: [Self; 16] = [
        Self::Cross,
        Self::Circle,
        Self::Square,
        Self::Triangle,
        Self::DpadUp,
        Self::DpadDown,
        Self::DpadLeft,
        Self::DpadRight,
        Self::LeftBumper,
        Self::RightBumper,
        Self::LeftStick,
        Self::RightStick,
        Self::Create,
        Self::Options,
        Self::PlayStation,
        Self::Touchpad,
    ];

    #[must_use]
    pub const fn mask(self) -> u32 {
        1_u32 << (self as u8)
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum Axis {
    LeftX,
    LeftY,
    RightX,
    RightY,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Trigger {
    L2,
    R2,
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct StickValue(pub i16);

/// Fixed-size, allocation-free snapshot; cheap to copy on the input hot path.
///
/// `ControllerState::default()` is the neutral state: nothing pressed, sticks centered,
/// triggers released.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct ControllerState {
    buttons: u32,
    sticks: [StickValue; 4],
    pub left_trigger: u8,
    pub right_trigger: u8,
}

impl ControllerState {
    /// Internal button mask; only the low 16 bits defined by [`Button`] can be set.
    #[must_use]
    pub const fn buttons(&self) -> u32 {
        self.buttons
    }

    #[must_use]
    pub const fn stick(&self, axis: Axis) -> StickValue {
        self.sticks[axis as usize]
    }

    pub fn set_stick(&mut self, axis: Axis, value: StickValue) {
        self.sticks[axis as usize] = value;
    }

    #[must_use]
    pub const fn trigger(&self, trigger: Trigger) -> u8 {
        match trigger {
            Trigger::L2 => self.left_trigger,
            Trigger::R2 => self.right_trigger,
        }
    }

    pub fn set_button(&mut self, button: Button, pressed: bool) {
        if pressed {
            self.buttons |= button.mask();
        } else {
            self.buttons &= !button.mask();
        }
    }

    #[must_use]
    pub const fn is_pressed(&self, button: Button) -> bool {
        self.buttons & button.mask() != 0
    }

    /// Merges another physical controller into the single Remote Play state.
    ///
    /// Buttons use logical OR, triggers keep the greatest pressure, and each stick
    /// axis keeps the value with the greatest absolute magnitude. Equal-magnitude
    /// axes keep the existing value, making the lower [`ControllerId`] win because
    /// [`ControllerSet`] iterates in key order.
    pub fn merge_from(&mut self, other: &Self) {
        self.buttons |= other.buttons;
        self.left_trigger = self.left_trigger.max(other.left_trigger);
        self.right_trigger = self.right_trigger.max(other.right_trigger);
        for (current, candidate) in self.sticks.iter_mut().zip(other.sticks) {
            if candidate.0.unsigned_abs() > current.0.unsigned_abs() {
                *current = candidate;
            }
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TransitionKind {
    Button {
        button: Button,
        pressed: bool,
    },
    /// The trigger crossed between released (0) and engaged (> 0).
    Trigger {
        trigger: Trigger,
        engaged: bool,
    },
}

/// A discrete transition that must never be coalesced or silently dropped.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct InputTransition {
    pub controller: ControllerId,
    pub sequence: u64,
    /// Monotonic event time in nanoseconds, from the platform input event.
    pub event_time_ns: u64,
    pub kind: TransitionKind,
}

/// Upper bound of transitions one state change can produce: 16 buttons + 2 triggers.
pub const MAX_TRANSITIONS_PER_CHANGE: usize = 18;

/// Allocation-free list of the transitions between two states.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TransitionBatch {
    len: usize,
    items: [Option<InputTransition>; MAX_TRANSITIONS_PER_CHANGE],
}

impl TransitionBatch {
    const EMPTY: Self = Self {
        len: 0,
        items: [None; MAX_TRANSITIONS_PER_CHANGE],
    };

    fn push(&mut self, transition: InputTransition) {
        self.items[self.len] = Some(transition);
        self.len += 1;
    }

    #[must_use]
    pub const fn len(&self) -> usize {
        self.len
    }

    #[must_use]
    pub const fn is_empty(&self) -> bool {
        self.len == 0
    }

    pub fn iter(&self) -> impl Iterator<Item = InputTransition> + '_ {
        self.items[..self.len].iter().flatten().copied()
    }
}

/// Derives the discrete transitions from `previous` to `next`, in internal button order
/// followed by L2 then R2. Stick and intermediate trigger changes produce none.
#[must_use]
pub fn diff(
    controller: ControllerId,
    previous: &ControllerState,
    next: &ControllerState,
    event_time_ns: u64,
    sequence: u64,
) -> TransitionBatch {
    let mut batch = TransitionBatch::EMPTY;
    let mut emit = |kind| {
        batch.push(InputTransition {
            controller,
            sequence,
            event_time_ns,
            kind,
        });
    };
    for button in Button::ALL {
        let pressed = next.is_pressed(button);
        if previous.is_pressed(button) != pressed {
            emit(TransitionKind::Button { button, pressed });
        }
    }
    for trigger in [Trigger::L2, Trigger::R2] {
        let engaged = next.trigger(trigger) > 0;
        if (previous.trigger(trigger) > 0) != engaged {
            emit(TransitionKind::Trigger { trigger, engaged });
        }
    }
    batch
}

/// A batch did not fit; nothing from it was queued.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct QueueOverflow {
    pub rejected: usize,
}

/// Bounded FIFO for discrete input edges.
#[derive(Clone, Debug)]
pub struct InputTransitionQueue {
    capacity: NonZeroUsize,
    pending: VecDeque<InputTransition>,
    overflows: u64,
}

impl InputTransitionQueue {
    #[must_use]
    pub fn new(capacity: NonZeroUsize) -> Self {
        Self {
            capacity,
            pending: VecDeque::with_capacity(capacity.get()),
            overflows: 0,
        }
    }

    /// Queues the whole batch or none of it, so a press is never kept without its
    /// release. An overflow is counted and must be surfaced by the caller.
    pub fn push_batch(&mut self, batch: &TransitionBatch) -> Result<(), QueueOverflow> {
        if self.capacity.get() - self.pending.len() < batch.len() {
            self.overflows += 1;
            return Err(QueueOverflow {
                rejected: batch.len(),
            });
        }
        self.pending.extend(batch.iter());
        Ok(())
    }

    pub fn pop(&mut self) -> Option<InputTransition> {
        self.pending.pop_front()
    }

    #[must_use]
    pub fn len(&self) -> usize {
        self.pending.len()
    }

    #[must_use]
    pub fn is_empty(&self) -> bool {
        self.pending.is_empty()
    }

    /// Number of rejected batches since creation; any non-zero value is a test failure.
    #[must_use]
    pub const fn overflows(&self) -> u64 {
        self.overflows
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ControllerUpdateError {
    ReservedId(ControllerId),
    Capacity {
        capacity: usize,
        rejected: ControllerId,
    },
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct MergedControllerUpdate {
    pub state: ControllerState,
    pub transitions: TransitionBatch,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct ControllerSet {
    controllers: BTreeMap<ControllerId, ControllerState>,
}

impl ControllerSet {
    /// Updates one physical controller and derives edges from the merged state.
    ///
    /// Remote Play currently has one proven controller state per session. Physical
    /// controllers remain isolated in this set for diagnostics and reconnect, but
    /// callers must send only the returned merged state and transitions.
    pub fn update_merged(
        &mut self,
        id: ControllerId,
        state: ControllerState,
        event_time_ns: u64,
        sequence: u64,
    ) -> Result<MergedControllerUpdate, ControllerUpdateError> {
        if id == REMOTE_PLAYER {
            return Err(ControllerUpdateError::ReservedId(id));
        }
        if !self.controllers.contains_key(&id) && self.controllers.len() >= MAX_LOCAL_CONTROLLERS {
            return Err(ControllerUpdateError::Capacity {
                capacity: MAX_LOCAL_CONTROLLERS,
                rejected: id,
            });
        }
        let previous = self.merged_state();
        self.controllers.insert(id, state);
        let next = self.merged_state();
        Ok(MergedControllerUpdate {
            state: next,
            transitions: diff(REMOTE_PLAYER, &previous, &next, event_time_ns, sequence),
        })
    }

    pub fn remove_merged(
        &mut self,
        id: ControllerId,
        event_time_ns: u64,
        sequence: u64,
    ) -> Option<MergedControllerUpdate> {
        let previous = self.merged_state();
        self.controllers.remove(&id)?;
        let next = self.merged_state();
        Some(MergedControllerUpdate {
            state: next,
            transitions: diff(REMOTE_PLAYER, &previous, &next, event_time_ns, sequence),
        })
    }

    #[must_use]
    pub fn get(&self, id: ControllerId) -> Option<&ControllerState> {
        self.controllers.get(&id)
    }

    #[must_use]
    pub fn merged_state(&self) -> ControllerState {
        let mut merged = ControllerState::default();
        for state in self.controllers.values() {
            merged.merge_from(state);
        }
        merged
    }

    #[must_use]
    pub fn len(&self) -> usize {
        self.controllers.len()
    }

    #[must_use]
    pub fn is_empty(&self) -> bool {
        self.controllers.is_empty()
    }
}

#[cfg(test)]
mod tests {
    use std::num::NonZeroUsize;

    use super::{
        Axis, Button, ControllerId, ControllerSet, ControllerState, ControllerUpdateError,
        InputTransitionQueue, MAX_TRANSITIONS_PER_CHANGE, QueueOverflow, REMOTE_PLAYER, StickValue,
        TransitionKind, Trigger, diff,
    };

    const PAD: ControllerId = ControllerId(1);

    fn queue(capacity: usize) -> InputTransitionQueue {
        InputTransitionQueue::new(NonZeroUsize::new(capacity).expect("non-zero"))
    }

    fn kinds(previous: &ControllerState, next: &ControllerState) -> Vec<TransitionKind> {
        diff(PAD, previous, next, 0, 0)
            .iter()
            .map(|t| t.kind)
            .collect()
    }

    #[test]
    fn preserves_wire_exact_stick_extremes() {
        let mut state = ControllerState::default();
        state.set_stick(Axis::LeftX, StickValue(i16::MIN));
        state.set_stick(Axis::RightX, StickValue(i16::MAX));
        assert_eq!(state.stick(Axis::LeftX), StickValue(i16::MIN));
        assert_eq!(state.stick(Axis::RightX), StickValue(i16::MAX));
        assert_eq!(state.stick(Axis::LeftY), StickValue(0));
        assert_eq!(state.stick(Axis::RightY), StickValue(0));
    }

    #[test]
    fn keeps_controllers_separate() {
        let mut set = ControllerSet::default();
        let first = ControllerState {
            left_trigger: 192,
            ..ControllerState::default()
        };
        set.update_merged(ControllerId(1), first, 0, 0)
            .expect("first slot");
        set.update_merged(ControllerId(2), ControllerState::default(), 0, 1)
            .expect("second slot");
        assert_eq!(
            set.get(ControllerId(1)).map(|state| state.left_trigger),
            Some(192)
        );
        assert_eq!(
            set.get(ControllerId(2)).map(|state| state.left_trigger),
            Some(0)
        );
    }

    #[test]
    fn merged_buttons_release_only_after_every_controller_releases() {
        let mut set = ControllerSet::default();
        let mut pressed = ControllerState::default();
        pressed.set_button(Button::Cross, true);

        let first = set
            .update_merged(ControllerId(1), pressed, 10, 1)
            .expect("first slot");
        assert_eq!(
            first
                .transitions
                .iter()
                .map(|item| item.kind)
                .collect::<Vec<_>>(),
            [TransitionKind::Button {
                button: Button::Cross,
                pressed: true,
            }]
        );
        assert!(
            set.update_merged(ControllerId(2), pressed, 11, 2)
                .expect("second slot")
                .transitions
                .is_empty()
        );
        assert!(
            set.update_merged(ControllerId(1), ControllerState::default(), 12, 3)
                .expect("existing slot")
                .transitions
                .is_empty()
        );
        let last = set
            .update_merged(ControllerId(2), ControllerState::default(), 13, 4)
            .expect("existing slot");
        assert_eq!(
            last.transitions
                .iter()
                .map(|item| item.kind)
                .collect::<Vec<_>>(),
            [TransitionKind::Button {
                button: Button::Cross,
                pressed: false,
            }]
        );
        assert!(
            last.transitions
                .iter()
                .all(|item| item.controller == REMOTE_PLAYER)
        );
    }

    #[test]
    fn rejects_a_third_simultaneous_controller() {
        let mut set = ControllerSet::default();
        set.update_merged(ControllerId(1), ControllerState::default(), 0, 0)
            .expect("first slot");
        set.update_merged(ControllerId(2), ControllerState::default(), 0, 1)
            .expect("second slot");
        assert_eq!(
            set.update_merged(ControllerId(3), ControllerState::default(), 0, 2),
            Err(ControllerUpdateError::Capacity {
                capacity: 2,
                rejected: ControllerId(3),
            })
        );
        assert_eq!(set.len(), 2);
    }

    #[test]
    fn reserves_remote_player_zero_from_physical_controllers() {
        let mut set = ControllerSet::default();
        assert_eq!(
            set.update_merged(REMOTE_PLAYER, ControllerState::default(), 0, 0),
            Err(ControllerUpdateError::ReservedId(REMOTE_PLAYER))
        );
        assert!(set.is_empty());
    }

    #[test]
    fn removing_controllers_derives_release_from_the_merged_state() {
        let mut set = ControllerSet::default();
        let mut pressed = ControllerState::default();
        pressed.set_button(Button::Cross, true);
        set.update_merged(ControllerId(1), pressed, 1, 1)
            .expect("first slot");
        set.update_merged(ControllerId(2), pressed, 2, 2)
            .expect("second slot");

        assert!(
            set.remove_merged(ControllerId(1), 3, 3)
                .expect("first controller exists")
                .transitions
                .is_empty()
        );
        let last = set
            .remove_merged(ControllerId(2), 4, 4)
            .expect("second controller exists");
        assert_eq!(
            last.transitions
                .iter()
                .map(|item| item.kind)
                .collect::<Vec<_>>(),
            [TransitionKind::Button {
                button: Button::Cross,
                pressed: false,
            }]
        );
        assert!(
            last.transitions
                .iter()
                .all(|item| item.controller == REMOTE_PLAYER)
        );
        assert!(set.is_empty());
        assert_eq!(set.remove_merged(ControllerId(2), 5, 5), None);
    }

    #[test]
    fn merged_analog_policy_is_max_trigger_and_greatest_axis_magnitude() {
        let mut set = ControllerSet::default();
        let mut first = ControllerState {
            left_trigger: 90,
            ..ControllerState::default()
        };
        first.set_stick(Axis::LeftX, StickValue(-12_000));
        let mut second = ControllerState {
            left_trigger: 210,
            ..ControllerState::default()
        };
        second.set_stick(Axis::LeftX, StickValue(20_000));
        set.update_merged(ControllerId(1), first, 0, 0)
            .expect("first slot");
        let merged = set
            .update_merged(ControllerId(2), second, 0, 1)
            .expect("second slot")
            .state;
        assert_eq!(merged.left_trigger, 210);
        assert_eq!(merged.stick(Axis::LeftX), StickValue(20_000));
    }

    #[test]
    fn internal_button_masks_are_distinct_low_16_bits() {
        let mut seen = 0_u32;
        for button in Button::ALL {
            assert_eq!(seen & button.mask(), 0, "{button:?} reuses a bit");
            seen |= button.mask();
        }
        assert_eq!(seen, 0xFFFF);
        let mut state = ControllerState::default();
        for button in Button::ALL {
            state.set_button(button, true);
            assert!(state.is_pressed(button));
            state.set_button(button, false);
            assert!(!state.is_pressed(button));
        }
        assert_eq!(state.buttons(), 0);
    }

    #[test]
    fn press_and_release_in_one_interval_both_reach_the_queue() {
        let neutral = ControllerState::default();
        let mut pressed = neutral;
        pressed.set_button(Button::Cross, true);
        let mut queue = queue(4);
        queue
            .push_batch(&diff(PAD, &neutral, &pressed, 10, 1))
            .expect("fits");
        queue
            .push_batch(&diff(PAD, &pressed, &neutral, 11, 2))
            .expect("fits");
        let first = queue.pop().expect("press");
        let second = queue.pop().expect("release");
        assert_eq!(
            first.kind,
            TransitionKind::Button {
                button: Button::Cross,
                pressed: true
            }
        );
        assert_eq!((first.sequence, first.event_time_ns), (1, 10));
        assert_eq!(
            second.kind,
            TransitionKind::Button {
                button: Button::Cross,
                pressed: false
            }
        );
        assert_eq!((second.sequence, second.event_time_ns), (2, 11));
        assert!(queue.is_empty());
    }

    #[test]
    fn sticks_and_intermediate_trigger_values_are_state_not_transitions() {
        let mut previous = ControllerState {
            left_trigger: 10,
            ..ControllerState::default()
        };
        let mut next = previous;
        next.left_trigger = 200;
        next.set_stick(Axis::LeftX, StickValue(12_000));
        assert!(kinds(&previous, &next).is_empty());
        previous.left_trigger = 0;
        assert_eq!(
            kinds(&previous, &next),
            [TransitionKind::Trigger {
                trigger: Trigger::L2,
                engaged: true
            }]
        );
    }

    #[test]
    fn full_trigger_sweep_never_overflows_and_keeps_both_crossings() {
        let mut queue = queue(256);
        let mut previous = ControllerState::default();
        let sweep = (0..=255_u8).chain((0..=255_u8).rev());
        for (sequence, value) in (0_u64..).zip(sweep) {
            let next = ControllerState {
                right_trigger: value,
                ..previous
            };
            queue
                .push_batch(&diff(PAD, &previous, &next, sequence, sequence))
                .expect("a sweep must not overflow");
            previous = next;
        }
        let crossings: Vec<_> = std::iter::from_fn(|| queue.pop()).map(|t| t.kind).collect();
        assert_eq!(
            crossings,
            [
                TransitionKind::Trigger {
                    trigger: Trigger::R2,
                    engaged: true
                },
                TransitionKind::Trigger {
                    trigger: Trigger::R2,
                    engaged: false
                },
            ]
        );
        assert_eq!(queue.overflows(), 0);
    }

    #[test]
    fn neutral_state_releases_everything_held() {
        let mut held = ControllerState {
            left_trigger: 255,
            right_trigger: 1,
            ..ControllerState::default()
        };
        for button in Button::ALL {
            held.set_button(button, true);
        }
        let batch = diff(PAD, &held, &ControllerState::default(), 0, 0);
        assert_eq!(batch.len(), MAX_TRANSITIONS_PER_CHANGE);
        assert!(batch.iter().all(|t| match t.kind {
            TransitionKind::Button { pressed, .. } => !pressed,
            TransitionKind::Trigger { engaged, .. } => !engaged,
        }));
    }

    #[test]
    fn overflow_rejects_whole_batch_and_is_counted() {
        let neutral = ControllerState::default();
        let mut both = neutral;
        both.set_button(Button::Cross, true);
        both.set_button(Button::Circle, true);
        let mut queue = queue(1);
        assert_eq!(
            queue.push_batch(&diff(PAD, &neutral, &both, 0, 0)),
            Err(QueueOverflow { rejected: 2 })
        );
        assert!(queue.is_empty(), "no partial batch may be queued");
        assert_eq!(queue.overflows(), 1);
    }

    #[test]
    fn transitions_keep_their_controller() {
        let neutral = ControllerState::default();
        let mut pressed = neutral;
        pressed.set_button(Button::Options, true);
        let mut queue = queue(4);
        queue
            .push_batch(&diff(ControllerId(1), &neutral, &pressed, 5, 1))
            .expect("fits");
        queue
            .push_batch(&diff(ControllerId(2), &neutral, &pressed, 6, 1))
            .expect("fits");
        assert_eq!(queue.pop().map(|t| t.controller), Some(ControllerId(1)));
        assert_eq!(queue.pop().map(|t| t.controller), Some(ControllerId(2)));
    }
}
