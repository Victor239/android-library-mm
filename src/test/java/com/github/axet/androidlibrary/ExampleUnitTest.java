package com.github.axet.androidlibrary;

import com.github.axet.androidlibrary.app.Storage;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void storageName() throws Exception {
        File f = new File("/tmp/abc (888).txt");
        File t = Storage.getNextFile(f);
    }
}