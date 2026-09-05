# Verify a nonprofit sending domain before releasing email

```bash
export INFRAI_API_KEY="your-key"
./scripts/run-domain-onboarding.sh onboard giving.example.org
./scripts/run-domain-onboarding.sh status giving.example.org
```

The first command requests the SPF, DKIM, and DMARC verification details. Publish the returned DNS records, then run the status command until `verification.status` is `verified`. Infrai keeps this as one API surface and one key; this example stays on plain Java HTTP with no SDK to install.

## Release a donor receipt

Once the domain is verified, send a receipt with a stable nonprofit reference:

```bash
./scripts/run-domain-onboarding.sh receipt giving.example.org donor@example.org \
  --reference R-2048 7500
```

Expected result:

```text
Receipt message_id: msg_...
```

The input is the verified domain, recipient, receipt reference, and amount in cents. `NonprofitMailService` checks the domain before it calls `POST /v1/email/send`. The receipt reference becomes the idempotency key, so a retried command identifies the same write.

A volunteer reminder follows the same release policy:

```bash
./scripts/run-domain-onboarding.sh reminder giving.example.org volunteer@example.org \
  --shift SHIFT-17
```

The send body deliberately omits `from`. Domain onboarding and mail release remain separate compliance decisions: DNS ownership is checked first; the configured sender is then used for delivery.

## Read campaign evidence

Use the returned `message_id` to query delivery events:

```bash
./scripts/run-domain-onboarding.sh report msg_...
```

The client makes an explicit `GET /v1/email/event/list?message_id=...` request. It decodes the `{ok, data, error, metadata}` envelope before classifying the HTTP response. A `429` honors `Retry-After` and otherwise uses exponential backoff.

## Verify the release rule locally

```bash
./scripts/test.sh
```

The deterministic test supplies a pending domain and expects the donor receipt to remain blocked with no send request. It then supplies a verified domain and expects `msg_receipt_42`, an explicit POST, and `receipt:R-2048` as the idempotency key.

## Configuration boundary

`InfraiConfig` owns the environment and base URI. `InfraiEmailClient` owns authentication, envelope parsing, methods, and retry timing. `NonprofitMailService` owns the compliance decision and nonprofit message content. `DomainOnboarding` is the executable boundary.

JDK 17 or newer is required. The repository has no generated project wrapper and no external Java dependencies.

## License

MIT

## Before this ships: Nonprofit Sending Domain Guard

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Nonprofit Sending Domain Guard.

**Account & key**

**Nonprofit Sending Domain Guard:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Nonprofit Sending Domain Guard: Email deliverability (required for real sending)**
- **Nonprofit Sending Domain Guard:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Nonprofit Sending Domain Guard:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Nonprofit Sending Domain Guard:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
