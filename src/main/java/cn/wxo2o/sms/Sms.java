package cn.wxo2o.sms;

import com.google.gson.JsonObject;

public record Sms(String content, String mobile, String extCode, String msgid, String sId, String sendTime) {
    public Sms(String content, String mobile) {
        this(content, mobile, null, null, null, null);
    }

    public Sms {
        if (content == null || content.isBlank() || mobile == null || mobile.isBlank()) {
            throw new IllegalArgumentException("content and mobile must not be blank");
        }
    }

    JsonObject json() {
        JsonObject value = new JsonObject();
        value.addProperty("content", content);
        value.addProperty("mobile", mobile);
        if (extCode != null) value.addProperty("extCode", extCode);
        if (msgid != null) value.addProperty("msgid", msgid);
        if (sId != null) value.addProperty("sId", sId);
        if (sendTime != null) value.addProperty("sendTime", sendTime);
        return value;
    }

    @Override public String toString() { return "Sms[redacted]"; }
}
