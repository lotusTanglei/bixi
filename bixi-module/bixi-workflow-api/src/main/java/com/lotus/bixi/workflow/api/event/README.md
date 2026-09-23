# Workflow Lifecycle Event Contract (Stage 2B)

`WorkflowEventCodec` encodes and decodes four fixed version-1 messages. It uses its
own strict JSON mapper and UTF-8 decoder; HTTP ObjectMapper settings do not alter
the wire format. Integer fields use exact numeric values: integral exponent forms
produced by durable-message canonicalization are accepted without floating-point
rounding; fractions, strings, and values outside the declared integer range are
rejected. Encoding uses JSON integer literals. Timestamps are UTC ISO-8601
`Instant` strings, and unknown/duplicate fields, trailing data, invalid Unicode,
unknown event types, or unknown schema versions are rejected.

| Type | Source -> target | Process ID | Aggregate sequence | Payload |
| --- | --- | --- | --- | --- |
| `WORKFLOW_START_REQUESTED` | `upms` -> `workflow` | null | 0 | title, approverId, requestHash |
| `WORKFLOW_STARTED` | `workflow` -> `upms` | required | 1 | requestHash |
| `WORKFLOW_START_REJECTED` | `workflow` -> `upms` | null | 1 | requestHash, errorCode |
| `WORKFLOW_COMPLETED` | `workflow` -> `upms` | required | >1 | requestHash, outcome, endedAt |
| `WORKFLOW_BUSINESS_TASK_REQUESTED` | `workflow` -> `upms` | required | >1 | requestHash, operationId, executionId, activityId, activityOccurrence, deadline |
| `WORKFLOW_BUSINESS_TASK_RESULT` | `upms` -> `workflow` | required | >1 | requestHash, operationId, success, bookingReference/errorCode, completedAt |
| `WORKFLOW_COMPENSATION_REQUESTED` | `workflow` -> `upms` | required | >1 | requestHash, operationId, compensationId |
| `WORKFLOW_COMPENSATION_RESULT` | `upms` -> `workflow` | required | >1 | requestHash, operationId, compensationId, success, errorCode, completedAt |

Every event is scoped to tenant `default`, model `demo_leave_approval`, table
`demo_leave_request`, a positive businessId and round, and an immutable businessKey.
The payload's requestHash is the original UPMS submission/start request hash,
including on completion. `commandId` identifies that original submission command;
subsequent task command audit remains in `wf_command`. The eventId is unique per
message. correlationId links the submission, and causationId may name the prior
event; the consumer must verify these against locally persisted command and business
identity. A completed event can arrive before the started event and still supply
the same original requestHash and association for later binding.

`WorkflowActorSnapshot` is the UPMS-authorized submitter snapshot. Its
originatingService stays `upms` even when the outer event source is `workflow`;
approval actors are recorded in workflow command/approval audit. The snapshot does
not authenticate the sender. Transport adapters must authenticate the fixed source
through their local service registry and Rabbit permissions, compare durable-message
headers with the decoded envelope, and authorize the business association using
local state. This API contains no transport, persistence, Spring security context,
callback URL, or dynamic Java subtype loading.

StartRequested carries only title and approverId for the current leave model. UPMS
retains the validated leave form and submission command locally; the workflow
consumer must not accept arbitrary process variables from this event. StartRejected
uses the fixed codes `INVALID_START`, `DEFINITION_UNAVAILABLE`,
`APPROVER_UNAVAILABLE`, and `START_FAILED`. Completion outcomes are limited to
`APPROVED`, `REJECTED`, and `CANCELED`. Automatic-task events use fixed UUID
operation/compensation identifiers and do not carry callback URLs or arbitrary
process variables. The 2E event contract is implemented, but its Flowable v2
producer/consumer chain remains a later slice.
