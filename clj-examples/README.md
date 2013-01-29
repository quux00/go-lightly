## Go-lightly Clojure examples

To demonstrate the use of the go-lightly library and programming in the CSP style of Go, I provide a number of example implementations.

Most are based on Go examples from Pike's talk or examples on the [golang website](http://golang.org/).

Below is a quick summary of each and how to run it.  All can be run from the REPL or the command line.

### conc-prime-sieve

*namespace*: thornydev.primes.conc-prime-sieve

Based on Go implementation at: http://play.golang.org/p/9U22NfrXeq

It is an implementation of a prime sieve using go routines and channels.

Rob Pike said of this example:
> There's a legendary example called the concurrent prime sieve, which is kind of an amazing thing. It was the first truly beautiful concurrent program I think I ever saw. 

This example is important because it shows how to pipeline channels and shows a case where synchronous channels are needed.  This example would not work efficiently with buffered channels.  You need to have the producers of the numbers pause and wait for the consumers to take the next number so that one doesn't run ahead of the other.

**How to run**

From the repl:

```clj
user=> (require '[thornydev.go-lightly.examples.primes.conc-prime-sieve :as pr])
nil
user=> (pr/sieve-main)  ;; will print the first 10 prime numbers
...
user=> (pr/sieve-main 100)  ;; or specify how many prime numbers you want
...
```

From the command line, you can use the default that runs the first 10:

    lein run :primes


### "google" search examples

*namespace*: thornydev.search.google

In his 2012 Google IO presentation, Pike builds up this example in Go, so this is the Clojure version.  The intermediate versions are there, labeled "google-1", "google-2", etc.  The final version is `google-3`.

This example demonstrates a fake search scenario where you want to query a set of resources that have variable latency, but always return whatever values you have within a limited time frame - 80 milliseconds.  It uses timeouts to enforce the 80 ms window and spawns up two parallel searches of each resource, hoping that one will return within the timeout window.  It takes the first response from each category and ignores the second.


**Not yet finished**
