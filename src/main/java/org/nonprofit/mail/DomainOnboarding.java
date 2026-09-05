package org.nonprofit.mail;

import java.util.Map;

public final class DomainOnboarding {
    private DomainOnboarding() {}

    public static void main(String[] args) {
        if (args.length < 2) usage();
        NonprofitMailService service = new NonprofitMailService(
                new InfraiEmailClient(InfraiConfig.fromEnvironment()));
        try {
            switch (args[0]) {
                case "onboard" -> {
                    Map<String, Object> result = service.beginDomainOnboarding(args[1]);
                    System.out.println("Domain onboarding response: " + result);
                }
                case "status" -> System.out.println("Verification status: " + service.domainStatus(args[1]));
                case "receipt" -> {
                    if (args.length != 6) usage();
                    long cents = Long.parseLong(args[5]);
                    String id = service.sendApproved(args[1], new NonprofitMailService.MailRequest(
                            NonprofitMailService.MailKind.DONOR_RECEIPT, args[2], args[4], cents));
                    System.out.println("Receipt message_id: " + id);
                }
                case "reminder" -> {
                    if (args.length != 5) usage();
                    String id = service.sendApproved(args[1], new NonprofitMailService.MailRequest(
                            NonprofitMailService.MailKind.VOLUNTEER_REMINDER, args[2], args[4], 0));
                    System.out.println("Reminder message_id: " + id);
                }
                case "report" -> System.out.println("Campaign events: " + service.campaignReport(args[1]));
                default -> usage();
            }
        } catch (InfraiException error) {
            System.err.println("Request rejected: " + error.code() + " (HTTP " + error.status() + ")");
            System.exit(error.status() >= 400 && error.status() < 500 ? 2 : 3);
        }
    }

    private static void usage() {
        System.err.println("Commands:");
        System.err.println("  onboard <domain>");
        System.err.println("  status <domain>");
        System.err.println("  receipt <domain> <to> --reference <id> <amount-cents>");
        System.err.println("  reminder <domain> <to> --shift <id>");
        System.err.println("  report <message-id>");
        System.exit(64);
    }
}
