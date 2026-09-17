package com.pinghome.app.data.api;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;

/** minSdk 26 has native java.time support, so DTOs use java.time.Instant directly instead of
 * hand-parsing ISO-8601 strings everywhere the backend returns a timestamp. */
public class InstantTypeAdapter extends TypeAdapter<Instant> {

    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.toString());
        }
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
        String value = in.nextString();
        return value == null ? null : Instant.parse(value);
    }
}
