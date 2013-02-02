# Go-lightly

## Overview

Go-lightly is a Clojure library that facilitates building concurrent programs in Clojure in the style built into the Go language.  Go concurrency is based on the [Communicating Sequential Processes](http://en.wikipedia.org/wiki/Communicating_sequential_processes) (CSP) model of programming.  

CSP addresses concurrency interaction patterns - how separate processes, threads or routines communicate and coordinate with each other via **message passing**. A CSP language or library is intended to provided constructs that reduce the complexity of inter-process/inter-thread communication using primitives that are easy to use and reason about. This means not having to be a deep expert in a system's memory model in order to do concurrent programming. Instead, it hides semaphores, mutexes, barriers and other low level concurrency constructs in higher-level abstractions.

<br>
### Structure of this repo

This repo is split into two parts:

1. the core go-lightly library in the `go-lightly` directory, which is a lein project. go-lightly is now [available on Clojars](https://clojars.org/thornydev/go-lightly).
2. a set of examples of CSP-style concurrent programming using Go concurrency constructs using in Clojure and Go

To run the Go examples you will need Go installed.  See the README in `go-lightly-examples/go-examples/`.

The clojure examples are in a lein project.  See the README in `go-lightly-examples/clj-examples/`.


<br>
### go-lightly library

The core constructs of the Go concurrency programming are:

1. Go routines
2. Synchronous (blocking) channels
3. Bounded, mostly asynchronous (non-blocking) channels
4. A `select` operator that reads the next available message from multiple channels
5. Timeout operations on channel read/writes/selects

The go-lightly library provides all of these (plus a few extras) by wrapping features already provided in the JVM and Clojure.  It is composed of one file: the thornydev.go-lightly.core namespace that defines helper macros and functions.  There is a test for it in the usual spot (using lein project structure).

<br>
### go-lightly and Go examples

I have provided a number of usage examples that I assembled or wrote while thinking about how to develop this library.

There are basically 4 categories of examples you'll see as you peruse the examples:

1. Examples in Go in the go-examples directory from Rob Pike and golang website.  See the README in the go-examples directory on how to set up to run them.
2. Examples in Clojure the clj-examples directory include:
  1. Examples using Java's SynchronousQueue, TransferQueue and LinkedBlockingQueue as Go channels
  2. Examples using the Clojure [lamina](https://github.com/ztellman/lamina) library as Go channels
    * Some of these are taken from gists done by Alexey Kachayev in thinking about how to go CSP Go-style programming in Clojure
  3. Examples using the go-lightly library

Each example can be loaded up and run in the REPL.

Because I want to make sure all of these will run and end gracefully (not hang), I also set up a massive case statement in the `thornydev.go-lightly.run-examples/-main` method to run any of these via `lein run`.  Most can run be run with other targets, but some cannot since they take additional arguments.  See the run-examples.clj file for details.

Example:

    $ lein run :gen-amp :gen-lam1 :google-3-alpha

will run all three of those targets sequentially.

For details on the most important examples see [the README](https://github.com/midpeter444/go-lightly/tree/master/clj-examples) in the clj-examples directory.


## License

Copyright © 2012 Michael Peterson

Some of the example code in the go-examples directory is copyright Rob Pike or and some in go-examples and clj-examples is copyright [Alexey Kachayev](https://github.com/kachayev).

Distributed under the Eclipse Public License, the same as Clojure.
