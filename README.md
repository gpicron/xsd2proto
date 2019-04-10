# XSD2THRIFT  [![CircleCI](https://circleci.com/gh/entur/xsd2thrift.svg?style=svg)](https://circleci.com/gh/entur/xsd2thrift)

This tool allows for converting XML Schema files (.xsd) to Thrift (.thrift) and
Protocol Buffers (.proto).

## Binary download

To be updated

## Importing from Maven

To be updated

## Building

To build xsd2thrift, you need Maven (http://maven.apache.org/) installed. To build xsd2thrift, run `mvn install`.

xsd2thrift has been tested on Java SE 6.

## Usage

```bash
Usage: java xsd2thrift.jar [--thrift] [--protobuf] [--output=FILENAME]
                           [--package=NAME] filename.xsd

  --thrift                    : convert to Thrift
  --protobuf                  : convert to Protocol Buffers
  --filename=FILENAME           : store the result in FILENAME instead of standard output
  --package=NAME              : set namespace/package of the output file
  --nestEnums=true|false      : nest enum declaration within messages that reference them, only supported by protobuf, defaults to true
  --splitBySchema=true|false  : split output into namespace-specific files, defaults to false
  --customMappings=a:b,x:y    : represent schema types as specific output types
  --protobufVersion=2|3       : if generating protobuf, choose the version (2 or 3)
  --typeInEnums=true|false    : include type as a prefix in enums, defaults to true
  ```

## License

The code contributed for this package is licensed under LGPL v3 (see LICENSE).

XSOM is covered by GPL v2 with classpath exception (see LICENSE-xsom.1).

XSOM internally uses an URI class, which is copyrighted by Thai Open Source
Center (see LICENSE-xsom.2).

## Contact

Any feedback will be greatly appreciated, at the GitHub project page
(http://github.com/tranchis/xsd2thrift) or at tranchis_-_AT_-_gmail.com.

## Contributors (@github.com)

* tranchis
* p14n
* Fred-dy-
* pfisterer
* wesyoung
* dabble
* ae589

