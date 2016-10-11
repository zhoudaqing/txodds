# txodds scala test project

[![Build Status](https://travis-ci.org/zainab-ali/txodds.svg?branch=master)](https://travis-ci.org/zainab-ali/txodds) 
[![codecov](https://codecov.io/gh/zainab-ali/txodds/branch/master/graph/badge.svg)](https://codecov.io/gh/zainab-ali/txodds)

##Background
The project uses makes use of Akka to create a distributed system and handle TCP connections.
Akka-HTTP is used to expose rudimentary stats reports.
MongoDB is used for persistence.

## How to use
1. Start a MongoDB server on port 27017
2. Start the server using `sbt "runMain txodds.ServerApp"`
3. Start the writer using `sbt "runMain txodds.WriterApp"`
4. Start the reader using `sbt "runMain txodds.ReaderApp"`
