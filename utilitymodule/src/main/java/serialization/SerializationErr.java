package serialization;

import com.hayden.utilitymodule.result.error.SingleError;

public record SerializationErr(String getMessage) implements SingleError {

    public SerializationErr(Throwable th) {
        this(SingleError.parseStackTraceToString(th));
    }

}
