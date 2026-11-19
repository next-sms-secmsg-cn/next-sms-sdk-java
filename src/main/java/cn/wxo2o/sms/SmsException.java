package cn.wxo2o.sms;

import com.google.gson.JsonObject;
import java.io.IOException;

public class SmsException extends IOException {
    public enum Kind { HTTP, API, PROTOCOL, SIGNATURE, BUSY, CLOSED }
    private final Kind kind;
    private final Integer code;
    private final JsonObject response;

    public SmsException(Kind kind, Integer code) { this(kind, code, null); }

    public SmsException(Kind kind, Integer code, JsonObject response) {
        super("next-sms " + kind + (code == null ? "" : " code=" + code));
        this.kind = kind;
        this.code = code;
        this.response = response;
    }

    public Kind getKind() { return kind; }
    public Integer getCode() { return code; }
    public JsonObject getResponse() { return response; }
}
