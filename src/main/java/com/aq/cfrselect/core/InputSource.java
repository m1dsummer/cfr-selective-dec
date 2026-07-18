package com.aq.cfrselect.core;

import java.io.IOException;
import java.nio.file.Path;

interface InputSource {
    Path directClassFile();

    Path classPathRoot();

    String sourceKey();

    String fingerprint() throws IOException;
}
