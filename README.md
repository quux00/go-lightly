# Go-lightly

## Overview

Go-lightly is a Clojure library that facilitates building concurrent programs in Clojure in the style built into the Go language.  Go concurrency is based on the [Communicating Sequential Processes](http://en.wikipedia.org/wiki/Communicating_sequential_processes) (CSP) model of programming.  

CSP addresses concurrency interaction patterns - how separate processes, threads or routines communicate and coordinate with each other via **message passing**. A CSP language or library is intended to provided constructs that reduce the complexity of inter-process/inter-thread communication using primitives that are easy to use and reason about. This means not having to be a deep expert in a system's memory model in order to do concurrent programming. Instead, it hides semaphores, mutexes, barriers and other low level concurrency constructs in higher-level abstractions.

The core constructs of the Go concurrency programming are:

1. Go routines
2. Synchronous (blocking) channels
3. Bounded, mostly asynchronous (non-blocking) channels
4. A `select` operator that reads the next available message from multiple channels
5. Timeout operations on channel read/writes/selects

The go-lightly library provides all of these (plus a few extras) by wrapping features already provided in the JVM and Clojure.

In this overview, I introduce these Go concepts using Go code examples and terminology and then show how to use the go-lightly library to program with the concepts in Clojure.


### Compatibility Notes, Minimal Requirements and Dependencies

Go-lightly only works with Java 7 (and later) in order to use the java.util.concurrent.LinkedTransferQueue, which was added in Java 7.  See the [synchronous channels section below](#syncchan) for details on why this concurrent queue was chosen to implement Go synchronous channels.

Go-lightly has been tested with Clojure 1.3.0, 1.4.0 and 1.5-RC4.  The core library (and its test) are compatible with all three versions.  Some of the exmaples will **not** run under 1.3, but all will run with 1.4 and 1.5.

The core go-lightly library has no dependencies beyond Clojure and Java 7.  However, some of the example code requires Zach Tellman's lamina library, since I played with ways to emulate some Go-concurrency programming features using lamina.


## Getting Started

You can get an overview of all features of the go-lightly library from the "[learn from the REPL](https://github.com/midpeter444/go-lightly/wiki/Tutorial:-Learn-go%E2%88%92lightly-at-the-REPL)" section of the wiki.

The rest of [the wiki](https://github.com/midpeter444/go-lightly/wiki) (still in progress) covers additional notes and details not covered in the above tutorial.


## Updates

**02-Feb-2013:**
* Testing with Clojure 1.3, 1.4 and 1.5 complete.  See requirements section above for details.
* sleeping barbers example added

0.3.1 published on 28-Jan-2013.  It adds:

* a `selectf` function, which is a select control structure modeled after the select from Go.
* a load-balancer example (in clj-examples) that implements Pike's load-balancer exmaple in Go and shows why the `selectf` function is a necessary concept
* a `gox` macro that acts like the go macro, but wraps everything in a try/catch that:
  * ignores InterruptedException, allowing you to call (stop) on infinite go routines without any error printing to the screen
  * catching any other Exception and printing to stdout, since exceptions thrown in a Clojure future get swallowed and make it hard to debug during development
  * it is expected that you will using `gox` while developing and then change it to `go` for general availability/production, but you can stick with `gox` for production code if that suits you


## TODO

There is a race condition in the current implementation of select and selectf that needs to be handled correctly.


# Documentation

[The wiki](https://github.com/midpeter444/go-lightly/wiki) documents of the go-lightly API and shows some examples.

The clj-examples and go-examples directories have many examples of using Go-style concurrency constructs.

See all the thornydev.go-lightly.core-test that tests the go-lightly library.

## A note on namespaces

The GoChannel protocol defines two methods, `take` and `peek`, that conflict with function names in clojure.core.  I considered for quite a while what to call the "send" and "receive" and "look-without-taking" operations.  I decided against using `send` because that is the core operation on agents, which are a primary concurrency tool in Clojure, and I decided that would engender worse confusion.  I also could have put a star after all the function names like Zach Tellman does in the [lamina library](https://github.com/ztellman/lamina), but decided against it as well.

So to handle the name conflict, you have two options.  If you want to use or refer the entire go-lightly.core namespace into your namespace, you'll need to tell Clojure not to load clojure.core/take and clojure.core/peek:

```clj
(ns my.namespace
  (:refer-clojure :exclude [peek take])
  (:require [thornydev.go-lightly.core :refer :all]))
```      
      
Or you can simply use a namespace prefix to all the go-lightly.core functions:

```clj
(ns thornydev.go-lightly.examples.webcrawler.webcrawler
  (:require [thornydev.go-lightly.core :as go]))

(defn stop-frequency-reducer []
  (go/with-timeout 2000
    (let [back-channel (go/channel)]
      (go/put freq-reducer-status-channel {:msg :stop
                                           :channel back-channel})
      (go/take back-channel))))
```

The latter is the option I typically show in my examples.


## Usage

The go-lightly library is composed of one file: the thornydev.go-lightly.core namespace that defines helper macros and functions.  There is a test for it in the usual spot (using lein project structure).

In addition, I have provided a number of usage examples that I assembled or wrote while thinking about how to develop this library.

There are basically 4 categories of examples you'll see as you peruse the examples:

1. Examples in Go in the go-examples directory from Rob Pike and golang website.  See the README in the go-examples directory on how to set up to run them.
2. Examples in Clojure the clj-examples directory include:
  1. Examples using Java's SynchronousQueue, TransferQueue and LinkedBlockingQueue as Go channels
  2. Examples using the Clojure [lamina](https://github.com/ztellman/lamina) library as Go channels
    * Some of these are taken from gists done by Alexey Kachayev in thinking about how to go CSP Go-style programming in Clojure
  3. Examples using the go-lightly library

Each example can be loaded up and run in the REPL.

Because I want to make sure all of these will run and end gracefully (not hang), I also set up a massive case statement in the `thornydev.go-lightly.examples.run-examples/-main` method to run any of these via `lein run`.  Most can run be run with other targets, but some cannot since they take additional arguments.  See the run-examples.clj file for details.

Example:

    $ lein run :gen-amp :gen-lam1 :google-3-alpha

will run all three of those targets sequentially.

For details on the most important examples see [the README](https://github.com/midpeter444/go-lightly/tree/master/clj-examples) in the clj-examples directory.


## Resources

The [go-lightly wiki](https://github.com/midpeter444/go-lightly/wiki)

While developing the library, I did some "thinking out loud" in a set of blog posts.  (Note these are out of date now for the go-lightly API, but still valid in terms of overall thoughts, ideas and approaches.)

* Part 1: [Go Concurrency Constructs in Clojure](http://thornydev.blogspot.com/2013/01/go-concurrency-constructs-in-clojure.html)
* Part 2: [Go Concurrency Constructs in Clojure: select](http://thornydev.blogspot.com/2013/01/go-concurrency-constructs-in-clojure2.html)
* Part 3: [Go Concurrency Constructs in Clojure: why go-lightly?](http://thornydev.blogspot.com/2013/01/go-concurrency-constructs-in-clojure3.html)
* Part 4: [Go Concurrency Constructs in Clojure: idioms and tradeoffs](http://thornydev.blogspot.com/2013/01/go-concurrency-constructs-in-clojure4.html)

#### Talks by Rob Pike:
* [Google I/O 2012 - Go Concurrency Patterns](http://www.youtube.com/watch?v=f6kdp27TYZs&feature=youtu.be)
* [Concurrency is not Parallelism](http://vimeo.com/49718712) ([slides here](https://rspace.googlecode.com/hg/slide/concur.html#landing-slide))
* [Google I/O 2010 - Load Balancer Example](https://www.youtube.com/watch?v=jgVhBThJdXc)


## License

Copyright © 2012 Michael Peterson

Some of the example code in the go-examples directory is copyright Rob Pike or and some in go-examples and clj-examples is copyright [Alexey Kachayev](https://github.com/kachayev).

Distributed under the Eclipse Public License, the same as Clojure.
