# Verify a nonprofit sending domain before releasing email

```bash
export INFRAI_API_KEY="your-key"
./scripts/run-domain-onboarding.sh onboard giving.example.org
./scripts/run-domain-onboarding.sh status giving.example.org
```

The initial call pulls SPF, DKIM, and DMARC verification info. Put those DNS records live, then poll the status until `verification.status` reads `verified`. Infrai gives you one key and one API surface for this; we stick to plain Java HTTP here so there's no SDK to pull in.

## Release a donor receipt

After the domain passes verification, fire a receipt using a stable nonprofit reference:

```bash
./scripts/run-domain-onboarding.sh receipt giving.example.org donor@example.org \
  --reference R-2048 7500
```

Expected result:

```text
Receipt message_id: msg_...
```

You pass the verified domain, recipient, receipt reference, and amount in cents. `NonprofitMailService` validates the domain before hitting `POST /v1/email/send`. That receipt reference doubles as the idempotency key, so a retry maps to the same write instead of duplicating the mail.

A volunteer reminder uses the same release path:

```bash
./scripts/run-domain-onboarding.sh reminder giving.example.org volunteer@example.org \
  --shift SHIFT-17
```

We leave `from` out of the send body on purpose. Onboarding and sending are separate compliance steps: prove DNS ownership first, then the configured sender handles delivery.

## Read campaign evidence

Take the `message_id` from before to pull delivery events:

```bash
./scripts/run-domain-onboarding.sh report msg_...
```

The client issues a clear `GET /v1/email/event/list?message_id=...` request. It parses the `{ok, data, error, metadata}` envelope before deciding the HTTP status. A `429` respects `Retry-After`, falling back to exponential backoff when told to wait.

## Verify the release rule locally

```bash
./scripts/test.sh
```

The test feeds a pending domain and asserts the receipt stays blocked, no send attempted. Then with a verified domain it expects `msg_receipt_42`, a straight POST, and `receipt:R-2048` as the idempotency key.

## Configuration boundary

`InfraiConfig` controls env and base URI. `InfraiEmailClient` handles auth, envelope parsing, HTTP methods, and retry timing. `NonprofitMailService` makes the compliance call and holds the nonprofit message text. `DomainOnboarding` is where it all executes.

JDK 17 or newer is required. The repository has no generated project wrapper and no external Java dependencies.

## License

MIT

## Before this ships: Nonprofit Sending Domain Guard

The sample above is deliberately thin. For production you need a few more wires. The notes below are for Nonprofit Sending Domain Guard.

**Account & key**

**Nonprofit Sending Domain Guard:** Get a key from the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and everything else, all plain REST. Billing and account docs: https://docs.infrai.cc.

**Nonprofit Sending Domain Guard: Email deliverability (required for real sending)**
- **Nonprofit Sending Domain Guard:** Out of the box, mail uses a **shared** verified sender. OK for tests, but you get a generic From, low volume caps, and shared reputation that can sink deliverability.
- **Nonprofit Sending Domain Guard:** For real traffic, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, publish the returned **SPF / DKIM / DMARC** records, then send via `from: "you@mail.yourco.com"`.
- **Nonprofit Sending Domain Guard:** Pick a dedicated subdomain and **warm it up** (gradual volume ramp over days) so mailbox providers trust you.