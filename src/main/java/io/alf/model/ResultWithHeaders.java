package io.alf.model;

import java.util.List;
import java.util.Map;

public class ResultWithHeaders<T> {

    public final T result;
    private final Map<String, List<String>> headers;


    public ResultWithHeaders(T result, Map<String, List<String>> headers) {
        this.result = result;
        this.headers = headers;
    }


    public String getHeader(String name) {
        return headers.get(name).stream().findFirst().orElse(null);
    }
}
