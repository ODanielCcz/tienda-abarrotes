package com.odcc.tienda.modules.sync.application.usecase;

import com.odcc.tienda.modules.sync.application.exception.SyncPayloadInvalidException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SyncServicePayloadValidationTest {

    private static final Method REQUIRE_MERGED_PAYLOAD = requireMergedPayloadMethod();

    @Test
    void shouldRejectMergedPayloadDeeperThanTwentyLevels() {
        Object nested = "value";
        for (int level = 0; level < 20; level++) nested = Map.of("nested", nested);
        assertInvalid(Map.of("nested", nested));
    }

    @Test
    void shouldRejectMergedPayloadWithMoreThanOneThousandNodes() {
        List<Object> nodes = new ArrayList<>();
        for (int index = 0; index < 1_001; index++) nodes.add(index);
        assertInvalid(Map.of("nodes", nodes));
    }

    @Test
    void shouldRejectMergedPayloadStringLongerThanSixteenKiB() {
        assertInvalid(Map.of("padding", "a".repeat(16 * 1024 + 1)));
    }

    private static void assertInvalid(Map<String, Object> payload) {
        InvocationTargetException exception = assertThrows(
            InvocationTargetException.class,
            () -> REQUIRE_MERGED_PAYLOAD.invoke(null, payload)
        );
        assertInstanceOf(SyncPayloadInvalidException.class, exception.getCause());
    }

    private static Method requireMergedPayloadMethod() {
        try {
            Method method = SyncService.class.getDeclaredMethod("requireMergedPayload", Map.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
