# corrlang-cli

While CorrLang has been moved to [Codeberg](https://codeberg.org/drstrudel/corrlang),
the CLI remains on GitHub for easier access.

The CorrLang CLI provides a command-line interface to work with CorrLang projects,
as well as installing and managing CorrLang services. 
It basically wraps a gRPC client that communicates with a CorrLang server daemon.

For producing native images, we are using GraalVM Community Edition, which is [licensed under
a GPLv2 license](https://www.graalvm.org/faq/#) with the Classpath Exception. Hence, this project is also GPLv2 licensed.

## Building

To build the project, you need to have GraalVM (version 25) installed with native-image support.
Then, you can use Gradle to build the native image:

```bash
./gradlew nativeImage
```

Alternatively, you can download pre-built releases from the [Releases page]. 