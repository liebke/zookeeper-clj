#!/bin/bash -e

lein jar
lein pom
lein deploy clojars
