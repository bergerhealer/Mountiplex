# Mountiplex
[Dev Builds](https://ci.mg-dev.eu/job/Mountiplex/)

General Purpose Java Reflection Library

Mountiplex delivers a two-hit solution for accessing the internals of a hidden implementation in Java.
A type conversion engine allows dynamic type changing, which can be used to translate between hidden and API
types. A reflection template engine allows a way to declare the internal structure of the implementation.

An example template declaration that shows most of what is possible:
```java
package some.secret.project;

import my.api.wrapper.Secret;

class SecretService {
#if version >= 1.1
    private int secretCount;
#else
    private int secretCount:nSecrets;
#endif

    public (Secret) TSecret getSecret(String name);
    public String getStatus();

    public String getSecretName(String name) {
        return instance.getSecret(name).getName();
    }

    <code>
    public void clearSecretCount() {
        setSecretCount(0);
    }
    </code>
}
```

At runtime this same declaration file will be parsed, preprocessing macros are executed and then the whole thing is loaded into a compiletime generated abstract model of the SecretService called SecretServiceHandle. Using this handle it is possible to interact with the implementation safely, using the name/type translations as declared in the template file.

The handle implementation is fully generated at runtime, avoiding reflection where possible to allow for maximum performance code execution.

Mountiplex is primarily used by [BKCommonLib](https://github.com/bergerhealer/BKCommonLib) to interact with the Minecraft Server internals.

This product includes software developed by the Apache Software Foundation (http://www.apache.org/)
