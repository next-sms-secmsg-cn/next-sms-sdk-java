package cn.secmsg.examples;

import cn.secmsg.NextSmsClient;
import cn.secmsg.Sms;

public final class SendExample {
    public static void main(String[] args) throws Exception {
        try (var client = new NextSmsClient(System.getenv("NEXT_SMS_SP_ID"), System.getenv("NEXT_SMS_SP_KEY"))) {
            var response = client.send(new Sms(System.getenv("NEXT_SMS_CONTENT"), System.getenv("NEXT_SMS_MOBILE")));
            String messageId = response.get("msgid").getAsString();
            System.out.println("API accepted; persist the returned message ID in your business workflow.");
        }
    }
}
