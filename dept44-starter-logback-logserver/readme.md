## Overview

This logback configuration sends logs to ELK via UDP, this is to avoid the limitations that plain syslog has,
e.g. no compression, multiline support and longer messages.
It sends logs to ELK if environment variabels `LOGSERVER_HOST` and `LOGSERVER_PORT` are defined and `dept44.logback.logserver.disabled` is set to false.

It always sends logs to standard out, whether this is enabled or not.

### Enable / Disable GELF appender

To disable the automatic GELF-appender set the property `dept44.logback.logserver.disabled` to true.

### Chunk Size

The max chunk size is set to 508 bytes per chunk and since GELF accepts a maximum of 128 chunks
this means it accepts a maximum of 508 * 128 = 65024 bytes compressed.
If you notice that some events in ELK are missing, set the `dept44.logback.logserver.maxchunksize` in your local properties to a bigger value, e.g. 8192
(some GELF components are limited to processing up to 8192 bytes).

### Override config

If needed, you can also provide your own logback-spring.xml in your service to override this configuration completely.
