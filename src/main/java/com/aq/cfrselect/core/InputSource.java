package com.aq.cfrselect.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

interface InputSource {
    InputStream open() throws IOException;

    default Path directClassFile() {
        return null;
    }

    default InputSource sibling(String siblingEntryName) {
        return null;
    }
}
