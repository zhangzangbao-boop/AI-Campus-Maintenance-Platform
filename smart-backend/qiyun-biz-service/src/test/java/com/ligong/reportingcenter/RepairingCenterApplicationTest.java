package com.ligong.reportingcenter;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class RepairingCenterApplicationTest {

    @Test
    void mainMethod_shouldExist_andBePublicStaticVoid() throws Exception {
        Class<?> cls = Class.forName("com.ligong.reportingcenter.RepairingCenterApplication");
        Method main = cls.getMethod("main", String[].class);

        assertNotNull(main, "main method should exist");
        assertTrue(Modifier.isPublic(main.getModifiers()), "main should be public");
        assertTrue(Modifier.isStatic(main.getModifiers()), "main should be static");
        assertEquals(void.class, main.getReturnType(), "main should return void");
    }

    @Test
    void applicationClass_shouldBePublic_andNotAbstract() throws Exception {
        Class<?> cls = Class.forName("com.ligong.reportingcenter.RepairingCenterApplication");

        assertTrue(Modifier.isPublic(cls.getModifiers()), "application class should be public");
        assertFalse(Modifier.isAbstract(cls.getModifiers()), "application class should not be abstract");
    }
}