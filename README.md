# corrlang-cli

While CorrLang has been moved to [Codeberg](https://codeberg.org/drstrudel/corrlang),
the CLI remains on GitHub for easier access.

The CorrLang CLI provides a command-line interface to work with CorrLang projects,
as well as installing and managing CorrLang services. 
It basically wraps a gRPC client that communicates with a CorrLang server daemon.

For producing native images, we are using GraalVM Community Edition, which is [licensed under
a GPLv2 license](https://www.graalvm.org/faq/#) with the Classpath Exception. Hence, this project is also GPLv2 licensed.



## Installation

The easiest way to install the CorrLang CLI `corrl` (and hence the CorrLang system itself), is by downloading one of the pre-installed binaries
from the [releases tab](https://github.com/webminz/corrlang-cli/releases/).

### Linux

### Windows

- Download the `corrl-windows-x64.zip` file and locate it in the explorer
- Right click, select "Extract all" and select a folder, where you will find it again, e.g. `C:\Users\<yourname>`.
- Open a PowerShell at the extraction location, and run CorrLang with `.\corrl.exe`.

### Mac OS X

Mac OS X are currently built for the ARM64 architecture (i.e. using Apple's new M-chip).

- Download the the `corrl-macos-arm64.gz` archive.
- Open a shell and locate the download folder.
- Extract the archive with `gunzip -N corrl-macos-arm64.gz`
- Copy the resulting `corrl` file to a directory on your `$PATH`, e.g.
```shell
mkdir -p ~/.bin 
cp corrl ~/.bin/
export PATH=$PATH:~/.bin
```
- Make sure that the file is executable: `chmod +x corrl`

If you are getting errors due to Apple's binary quarantine system, you can "whitelist" the file with:
```shell
xattr -d com.apple.quarantine corrl
```

## Building from Source

To build the project, you need to have GraalVM (version 25) installed with native-image support.
Then, you can use Gradle to build the native image:

```bash
./gradlew nativeImage
```

Alternatively, you can download pre-built releases from the [Releases page]. 
