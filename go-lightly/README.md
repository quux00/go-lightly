# Go-lightly

## Overview

Go-lightly is a Clojure library that facilitates building concurrent programs in Clojure in the style built into the Go language.  Go concurrency is based on the [Communicating Sequential Processes](http://en.wikipedia.org/wiki/Communicating_sequential_processes) (CSP) model of programming.  

CSP addresses concurrency interaction patterns - how separate processes, threads or routines communicate and coordinate with each other via **message passing**. A CSP language or library is intended to provided constructs that reduce the complexity of inter-process/inter-thread communication using primitives that are easy to use and reason about. This means not having to be a deep expert in a system's memory model in order to do concurrent programming. Instead, it hides semaphores, mutexes, barriers and other low level concurrency constructs in higher-level abstractions.

The core constructs of the Go concurrency programming model are:

1. Go routines
2. Synchronous (blocking) channels
3. Bounded, mostly asynchronous (non-blocking) channels
4. A `select` operator that reads the next available message from multiple channels
5. Timeout operations on channel read/writes/selects

The go-lightly library provides all of these (plus a few extras) by wrapping features already provided in the JVM and Clojure.

In this overview, I introduce these Go concepts using Go code examples and terminology and then show how to use the go-lightly library to program with the concepts in Clojure.


### Compatibility Notes, Minimal Requirements and Dependencies

Go-lightly only works with Java 7 (and later) in order to use the java.util.concurrent.LinkedTransferQueue, which was added in Java 7.  See the [synchronous channels section in the wiki](https://github.com/midpeter444/go-lightly/wiki/Channels) for details on why this concurrent queue was chosen to implement Go synchronous channels.

Go-lightly has been tested with Clojure 1.3.0, 1.4.0 and 1.5.  The core library (and its test) are compatible with all three versions.  Some of the examples will **not** run under 1.3, but all will run with 1.4 and 1.5.

The core go-lightly library has no dependencies beyond Clojure and Java 7.  However, some of the example code requires Zach Tellman's lamina library, since I played with ways to emulate some Go-concurrency programming features using lamina.


## Getting Started

You can get an overview of all features of the go-lightly library from the "[learn from the REPL](https://github.com/midpeter444/go-lightly/wiki/Tutorial:-Learn-go%E2%88%92lightly-at-the-REPL)" section of the wiki.

The rest of [the wiki](https://github.com/midpeter444/go-lightly/wiki) dives into each area more fully.


## Updates

**02-Mar-2013:**

v. 0.4.0 published

I have changed the namespace of go-lightly from `thornydev.go-lightly.core` to `thornydev.go-lightly` so 0.4.0 is **incompatible** with 0.3.2.

A new signed jar has been pushed to Clojars and promoted to release status.

The wiki and examples in clj-examples has been updated to match this ns change.


**02-Feb-2013:**

v. 0.3.2 published

* go-lightly is now [available on Clojars](https://clojars.org/thornydev/go-lightly/versions/0.4.0)
* race condition in the select functions was solved by adding a poll method to GoChannel (based on .poll from the Java concurrent queues)
* Testing with Clojure 1.3, 1.4 and 1.5 complete.  See requirements section above for details.

**28-Jan-2013**

v. 0.3.1 published. It adds:

* a `selectf` function, which is a select control structure modeled after the select from Go.
* a load-balancer example (in clj-examples) that implements Pike's load-balancer example in Go and shows why the `selectf` function is a necessary concept
* a `gox` macro that acts like the go macro, but wraps everything in a try/catch that:
  * ignores InterruptedException, allowing you to call (stop) on infinite go routines without any error printing to the screen
  * catching any other Exception and printing to stdout, since exceptions thrown in a Clojure future get swallowed and make it hard to debug during development
  * it is expected that you will using `gox` while developing and then change it to `go` for general availability/production, but you can stick with `gox` for production code if that suits you

----

# Documentation

[The wiki](https://github.com/midpeter444/go-lightly/wiki) documents of the go-lightly API and shows some examples.

The clj-examples and go-examples directories have many examples of using Go-style concurrency constructs.

See the [thornydev.go-lightly-test](https://github.com/midpeter444/go-lightly/blob/master/go-lightly/test/thornydev/go_lightly_test.clj) that tests the go-lightly library.

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

## Usage

The go-lightly library is composed of one file: the thornydev.go-lightly namespace that defines helper macros and functions.  There is a test for it in the usual spot (using lein project structure).

In addition, I have provided a number of usage examples that I assembled or wrote while thinking about how to develop this library.  See the [top level README](https://github.com/midpeter444/go-lightly) for more information.


## Resources

The [go-lightly wiki](https://github.com/midpeter444/go-lightly/wiki)

While developing the library, I did some "thinking out loud" in a set of blog posts.  (Note: I recently updated these blog posts to be current with the 0.4.0 version of go-lightly).

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

Distributed under the Eclipse Public License, the same as Clojure.
