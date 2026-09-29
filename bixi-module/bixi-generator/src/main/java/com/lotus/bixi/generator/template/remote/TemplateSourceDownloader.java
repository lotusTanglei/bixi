package com.lotus.bixi.generator.template.remote;

import java.net.URI;

@FunctionalInterface
public interface TemplateSourceDownloader {

	byte[] download(URI uri, int maximumBytes);
}
