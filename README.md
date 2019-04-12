# XSD2PROTO  [![CircleCI](https://circleci.com/gh/entur/xsd2thrift.svg?style=svg)](https://circleci.com/gh/entur/xsd2thrift)

This tool allows for converting XML Schema files (.xsd) to Protocol Buffers (.proto). It is based on xsd2thrift, but since Thrift support has been 
removed and protobuf retained the name has changed

## Binary download

To be updated

## Importing from Maven

To be updated


## Usage

```
Usage: java xsd2proto-<VERSION>.jar [--output=FILENAME]
                           [--package=NAME] filename.xsd

  --configFile=FILENAME           : path to configuration file

OR

  --filename=FILENAME             : store the result in FILENAME instead of standard output
  --package=NAME                  : set namespace/package of the output file
  --nestEnums=true|false          : nest enum declaration within messages that reference them, only supported by protobuf, defaults to true
  --splitBySchema=true|false      : split output into namespace-specific files, defaults to false
  --customMappings=a:b,x:y        : represent schema types as specific output types
  --protobufVersion=2|3           : if generating protobuf, choose the version (2 or 3)
  --typeInEnums=true|false        : include type as a prefix in enums, defaults to true
  --includeMessageDocs=true|false : include documentation of messages in output, defaults to true
  --includeFieldDocs=true|false   : include documentation for fields in output, defaults to true
```

## License

The code contributed for this package is licensed under LGPL v3 (see LICENSE).

XSOM is covered by GPL v2 with classpath exception (see LICENSE-xsom.1).

XSOM internally uses an URI class, which is copyrighted by Thai Open Source
Center (see LICENSE-xsom.2).

## Contributors (@github.com)

* tranchis
* p14n
* Fred-dy-
* pfisterer
* wesyoung
* dabble
* ae589
* seime
* lassetyr
