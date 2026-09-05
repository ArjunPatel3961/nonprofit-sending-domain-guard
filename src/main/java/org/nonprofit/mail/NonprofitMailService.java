package org.nonprofit.mail;

import java.util.Map;

public final class NonprofitMailService {
    public enum MailKind { DONOR_RECEIPT, VOLUNTEER_REMINDER }
    public record MailRequest(MailKind kind, String recipient, String reference, long amountCents) {}

    private final InfraiEmailClient client;

    public NonprofitMailService(InfraiEmailClient client) {
        this.client = client;
    }

    public String domainStatus(String domain) {
        Map<String, Object> data = client.getDomain(domain);
        return Json.string(Json.child(data, "verification"), "status");
    }

    public Map<String, Object> beginDomainOnboarding(String domain) {
        return client.verifyDomain(domain);
    }

    public String sendApproved(String domain, MailRequest request) {
        String status = domainStatus(domain);
        if (!"verified".equalsIgnoreCase(status)) {
            throw new IllegalStateException("Sending domain must be verified before nonprofit mail is released");
        }
        return switch (request.kind()) {
            case DONOR_RECEIPT -> client.send(
                    request.recipient(),
                    "Donation receipt " + request.reference(),
                    "<p>Thank you for your donation of " + dollars(request.amountCents())
                            + ". Receipt: " + request.reference() + ".</p>",
                    "receipt:" + request.reference());
            case VOLUNTEER_REMINDER -> client.send(
                    request.recipient(),
                    "Volunteer reminder " + request.reference(),
                    "<p>Your volunteer shift reference is " + request.reference() + ".</p>",
                    "volunteer:" + request.reference());
        };
    }

    public Map<String, Object> campaignReport(String messageId) {
        return client.listEvents(messageId);
    }

    private static String dollars(long cents) {
        return "$" + cents / 100 + "." + String.format("%02d", Math.abs(cents % 100));
    }
}
