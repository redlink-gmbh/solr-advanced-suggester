# Solr Suggester
_An improved Suggestion-Handler for Solr_

[![Build Status](https://github.com/redlink-gmbh/solr-advanced-suggester/actions/workflows/maven-build-and-deploy.yaml/badge.svg)](https://github.com/redlink-gmbh/solr-advanced-suggester/actions/workflows/maven-build-and-deploy.yaml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=redlink-gmbh_solr-advanced-suggester&metric=alert_status)](https://sonarcloud.io/dashboard?id=redlink-gmbh_solr-advanced-suggester)

[![Maven Central](https://img.shields.io/maven-central/v/io.redlink.solr/solr-advanced-suggester.png)](https://central.sonatype.com/artifact/io.redlink.solr/solr-advanced-suggester)
[![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/https/oss.sonatype.org/io.redlink.solr/solr-advanced-suggester.png)](https://oss.sonatype.org/#nexus-search;gav~io.redlink.solr~solr-advanced-suggester~~~)
[![Javadocs](https://www.javadoc.io/badge/io.redlink.solr/solr-advanced-suggester.svg)](https://www.javadoc.io/doc/io.redlink.solr/solr-advanced-suggester)
[![Apache 2.0 License](https://img.shields.io/github/license/redlink-gmbh/solr-advanced-suggester.svg)](https://www.apache.org/licenses/LICENSE-2.0)

This is an update of the [Vind Suggestion Handler](https://github.com/RBMHTechnology/vind) compatible with Solr 9+.

## Usage

1. Add the following library to your Solr's lib-directory:
    ```xml
    <dependency>
        <groupId>io.redlink.solr</groupId>
        <artifactId>solr-advanced-suggester</artifactId>
        <version>${suggester.version}</version>
    </dependency>
    ```
2. Within `solrconfig.xml` configure a dedicated suggestion-handler:
    ```xml
    <requestHandler name="/suggester"
                    class="io.redlink.solr.suggestion.SuggestionRequestHandler">
        <lst name="defaults">
            <!-- enable suggestions -->
            <str name="suggestion">true</str>
            <!-- create suggestions based on the following field -->
            <str name="suggestion.df">suggestions</str>
            <!-- limit the suggestions to 10 terms -->
            <str name="suggestion.term.limit">10</str>
        </lst>
    </requestHandler>
    ```
    For further details have a look at the solr-configuration used in the test.

## License
Free use of this software is granted under the terms of the Apache License Version 2.0.
See the [License](LICENSE.txt) for more details.
