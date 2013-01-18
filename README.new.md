# Go-lightly

## Overview

Go-lightly is a Clojure library that facilitates buildling concurrent programs in Clojure in the style built into the Go language.  Go concurrency is based on the [Communicating Sequential Processes](http://en.wikipedia.org/wiki/Communicating_sequential_processes) (CSP) model of programming.

The core concepts of the Go concurrency programming are:

1. Go routines
2. Synchronous (blocking) channels
3. Asynchronous (non-blocking) channels
4. A `select` operator that can choose to read the next available message from multiple channels
5. Timeout operations on channel read/writes/selects

The go-lightly library provides all of these (plus a few extras) by wrapping features already provided in the JVM and Clojure.


## Compatibility Notes, Minimal Requirements and Depedencies

Go-lightly only works with Java 7 and later in order to use the java.util.concurrent.LinkedTransferQueue, which was added in Java 7.  See the [synchronous channels section below](#syncchan) for details on why this concurrent queue was chosen to implemento Go synchronous channels.

Go-lightly has been tested with Clojure 1.3, 1.4 and 1.5 (**>>TODO: make this true<<**) and works with all of those, as long as Java 7 is provided.

The core go-lightly library has no dependencies beyond Clojure and Java 7.  However, I have provided a number of examples, some of which emulate Go-concurrency programming using Zach Tellman's lamina library.


## Go routines

Go routines in Go can be thought of as spawning a process in the background, as you do with the `&` symbol on a Unix/Linux command line.  Except in Go you are not spawning a process or even a thread.  You are spawning a routine or piece of control flow that will get multiplexed onto threads in order to run.  They are lighter weight than threads.  You can have more Go routines running than the possible number of threads supported in a process by your OS or runtime.

To create a Go routine you simply invoke a function call with `go` in front of it and it will run that function in another thread.  In this aspect, Go's `go` is very similar to Clojure's `future` function.

    func SayHello(name) {
      time.Sleep(2 * time.Second)
      print "Hello: " + name + "\n";
    }
    go SayHello("Fred");  // returns immediately
    // two seconds later prints "Hello Fred"
    
    ;; the Clojure version of the above (almost)
    (defn say-hello [name]  
      (Thread/sleep 2000)
      (println "Hello:" name))
    (future (say-hello "Fred"))


Like `go`, Clojure's `future` runs the function given to it in another thread.  However, `future` differs from Go routines in three important ways:

1. `future` pushes onto a thread from the Clojure agent thread pool where it remains until the future is finished.  So Clojure futures are not a lightweight multiplexed routine.  This is a limitation of the JVM environment and as far as I know there is not a way to emulate this aspect of Go routines on the JVM.  (If any knows differently, please let me know!)

2. The thread used by `future` is not a daemon-thread, where as Go routines are deamons.  In Go, when the main thread (as defined by the thread running through the `main` function) ends, any Go routines still running are immediately shut down.  Thus, you can span a Go routine that never shuts down or is hung in a blocking operation and the program will still exit gracefully.  This not true of Clojure future threads.  The go-lightly library provides some utilities to assist with this.

3. `future` returns a Future whereas invoking go does not.  If you need to wait on a go routine to finish or compute some value you have to use a channel for communcation (or, (not recommended) set some shared state for other thread to check).  With a Future you can wait on it to finish and return a value to you directly.

The go-lightly library provides a `go` function that internally invokes `future`, but ignores the Future that it returns.

When designing your concurrent programs in Clojure, think about whether you want to get the return value of the future and use that to coordinate threads.  If so, then you don't need go-lightly.  But if you want to spawn up "go routines" that will communicate via channels and treat those go routines like daemon threads, go-lightly facilitates that and makes it easy to do.  See [the wiki](xxx) for detailed examples.


## Synchronous Blocking Channels

In Go, when you create a channel with no arguments, you get a synchronous blocking channel:

    // Go version
    // returns a synchronous channel that takes int values
    ch := make(chan int)

    ;; Clojure version
    ;; Clojure channels are not typed - any value can be placed on it
    (def ch (go/channel))

Puts and takes, or "sends" and "receives" in Go's parlance, are done with the left arrow operator: `<-`. Any send on the channel will block until a receive is done by another thread:

    // Go version
    // blocks until value is received
    ch <- 42

    ;; Clojure version
    (go/put ch 42)

Likewise, any receive on the channel blocks until a send is done by another thread:

    // Go version
    // blocks until value is sent to the channel
    myval := <-ch
    
    // Clojure version
    (let [myval (go/take ch)]

In Go parlance, these are simply "channels", while nonblocking asynchronous channels are called "buffered channels", so I will use those terms from here forward.

The java.util.concurrent package includes a number of very nice concurrent queues, which are a superset of Go channels.  In particular, SynchronousQueue and the newly introduced LinkedTransferQueue can be used like Go channels.  It worth emphasizing that they also have functionality that Go channels do not provide.  The Go channels were intentionally designed to be minimally featured - they are constricted to be used in particular ways that facilitate the Go style of concurrent programming.  The go-lightly library similarly simplifies Java's TransferQueue to a minimal set of supported operations.  However, if you really need it, you can always grab the embedded TransferQueue out of the go-lightly channel and work with it directly through Clojure's Java interop features.

### Using Channels

Because they block, channels are useful only in a multi-threaded environment.  Like a CountDownLatch or a CyclicBarrier in Java, they are used to synchronize two threads with each other, one waiting until the other one finishes.  They also have the advantage of being able to send messages.  They can say more than "I'm done" or "I'm ready", they can deliver data to their recipient, such as the end result of a calculation or instructions on the next task to do.

You can also think of channels like Unix pipes, but channels can be used bidirectionally, though this is tricky and can lead to race conditions.  To illustrate: suppose we have a channel shared between two threads.  Thread A is the master and Thread B is the worker. Thread A sends instructions on what to do next and Thread B reports back when done with the result.  A simplification of their exchange could look like:

    // thread A
    ch <- ":taskA"
    taskA_result := <-ch
    ch <- ":taskB"
    taskB_result := <-ch
    
    // thread B
    instructionsA := <-ch
    // .. do some work
    ch <- resultA
    instructionsB := <-ch
    // .. do some work
    ch <- resultA
    
This is not going to work. Due to a race to read from the channel, thread A is sometimes going to consume it's own message and thread B is going to hang forever.  And if thread A doesn't consume it's own messages, thread B is likely to do that to itself as well at some point.

A better solution is to use two channels and treat each one as unidirectional.  [The wiki](xxx) has examples of doing this.

A key consideration when using channels is that you have to careful of coding yourself into a deadlock, particularly in Clojure.  Go has nice built-in deadlock detection and will issue a panic and tell you that all threads are deadlocked.  Java threads and therefore Clojure threads, just go into la-la land never to be heard from again.  You'd have to get a thread dump to see what's happened.  Thread dumps are your friend when doing concurrent programming with blocking communications.

There are ways to timeout and not block forever.  We will look at those when we get to the `select` feature.

### go-lightly channel abstraction

Finally, the go-lightly library has built a formal abstraction: the GoChannel protocol. 

    (defprotocol GoChannel
      (put [this val] "Put a value on a channel. May or may not block depending on type and circumstances.")
      (take [this] "Take the first value from a channel. May or may not block depending on type and circumstances.")
      (size [this] "Returns the number of values on the channel")
      (peek [this] "Retrieve, but don't remove, the first element on the channel. Never blocks.")
      (clear [this] "Remove all elements from the channel without returning them"))

There are other functions for channels not part of the GoChannel abstraction that will discussed below and in the wiki.

The specific GoChannel implementation type for Go channels is called Channel.


## Buffered Channels 

Buffered channels are bounded, asynchronous and mostly non-blocking. Puts (sends) will block only if the buffered channel is full and takes (receives) will block only if the channel is empty.

Buffered channels are thread-safe - multiple threads can be reading and writing from them at the same time.  As with channels, reads on buffered channels consume values, so only one thread can get any given value.  Java has a number of concurrent queues that match the Go buffered channel functionality.  The go-lightly library buffered channel wraps the LinkedBlockingQueue.

To create a buffered channel, you simply pass a numeric argument specifying the max capacity of the channel:

    // Go version
    ch := make(chan int, 128)

    ;; Clojure version
    (def ch (go/channel 128))

You use buffered channels whenever one or more threads needs to be pushing a stream of messages for one or more consumers to read and process on the other end.


## select

In Go the select statement is switch-like control structure that is very important to writing idiomatic Go concurrent applications.  In fact, Rob Pike, in his Google IO 2012 presentation on Go said:

    "The select statement is a key part of why concurrency is built into Go as features of the language, rather than just a library. It's hard to do control structures that depend on libraries."

The go-lightly library provides select-like functionality, but not exactly like Go does.  At present go-lightly's select is a function (or rather a suite of functions), not a control structure.  However, with Clojure macros, a control structure version could certainly be written if it is warranted.



## Misc Notes to put somewhere

When working with Go-style channels, it is very important to think through ordering of operations.  The promise of CSP-style concurrency is that you can apply standard linear thinking to concurrent applications.  You have to carefully consider where you need synchornization and where you do not.  That means paying attention to what operations will block and which will not.  Even though you don't have to think about locks, mutexes and semaphores, you can still code yourself into race conditions and deadlocks with Go channels and Go routines if you reason incorrectly.

