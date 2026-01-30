package com.hayden.utilitymodule.string;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilTest {

    @Test
    public void testStringEncode() {
        var s = StringUtils.repeat("1", 1000);
        var b = StringUtil.getSubstringByByteLength(s, 50, Charset.defaultCharset());
        assertTrue(b.isOk());
    }

}