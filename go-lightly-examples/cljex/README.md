## Go-lightly Clojure examples

To demonstrate the use of the go-lightly library and programming in the CSP style of Go, I provide a number of example implementations.

Most are based on Go examples from Pike's talk or examples on the [golang website](http://golang.org/).

Below is a quick summary of each and how to run it.  All can be run from the REPL or the command line.

* [conc-prime-sieve](#primeSieve)
* ["google" search examples](search)
* [load-balancer](#balancer)
* [chinese whispers](#whispers)
* [webcrawler](#webcrawler)

----

<a name="primeSieve"></a>
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

----

<a name="search"><
### "google" search examples

*namespace*: thornydev.search.google

In his 2012 Google IO presentation, Pike builds up this example in Go, so this is the Clojure version.  The intermediate versions are there, labeled "google-1", "google-2", etc.  The final version is `google-3`.

This example demonstrates a search scenario where you want to query a set of resources that have variable latency, but always return whatever values you have within a limited time frame - 80 milliseconds.  It uses timeouts to enforce the 80 ms window. It spawns up two parallel searches of each resource, hoping that one will return within the timeout window.  It takes the first response from each category and ignores the second.

**How to run**

From the REPL:

```clj
user=> (require '[thornydev.go-lightly.examples.search.google :as goog])
nil
user=> (goog/google-main :goog3.0)
[web1 result for 'clojure'
 video1 result for 'clojure'
 image2 result for 'clojure'
]
39 ms
```

From the command line:

    lein run :goog3.0


----

<a name="balancer"></a>
### load-balancer

**namespace**: thornydev.go-lightly.examples.load-balancer.balancer

Based on the load balancer example that Rob Pike presented at the 2010 Google IO conference and the 2012 Heroku conference.  I haven't found working code for this in Go yet.  This is a Clojure implementation using go-lightly based closely on the Go implementation snippets Pike showed.

Its big point is showing a case in which you need to use the `selectf` function, rather than just `select`.  With `select` you will get the next available value from a channel but you won't know what channel it came from.  If you need to know the channel and do something in response, use `selectf`, which is very similar to the `select` control structure in Go.

In this app, there are three roles:  requesters, workers and a balancer.  This is not modeled in an OO fashion.  A requester, balancer and work are data (maps) and have a primary function that they loop through.  They communicate via channels, some buffered, some synchronous.

Multiple requesters are spun up as go routines.  They loop inside the `requester` fn to put a piece of data onto the main work-channel.  That piece of data specifies a function for a worker to execute to execute (in this case calculate TAU) and a channel on which the worker should send back the result.  It synchronously waits on the result channel for the worker to send back the answer.

Multiple workers are spun also in their own go routines.  They loop inside the work function grabbing requests off their own request channel, executing the operation in the request and putting the result of the operation on the result channel in the request, which the requester is listening on.  Then it signals that it is done by putting itself on the "done-channel".

The done-channel is monitored by the balancer.  There is only one balancer.  It loops through its balance fn select from either the main work-channel, which the requesters put new requests on, or the done-channel, which the workers put themselves on when they are finished.

It acts as a load balancer because it keeps all the workers in a sorted set that is ordered by pending work load.  The least loader worker is given the next incoming task and when a worker reports itself as done, it is removed and added back to the sorted pool so it sits in the right place in the sort order.

The program runs until a certain number of requests are processed.

You can specify how many workers to spawn up (each in its own thread/go-lightly routine) and how many requests to process before shutting down.  Note that for every worker go-routine spun up, three requesters will be created as well.


**How to run**

From the REPL:

```clj
user=> (require '[thornydev.go-lightly.examples.load-balancer.balancer :as bal])
nil
;; spawn 22 workers, 66 requesters and run until 444 requests are processed
user=> (bal/-main 22 444)
```

From the command line, spawning 100 workers, 300 requesters and running until 2000 requests are processed

    lein run :balancer 100 2000


----

<a name="whispers"></a>
### chinese whispers

**namespace**: thornydev.go-lightly.examples.whispers.chinese-whispers

Pike shows this example (in Go) in his 2012 Heroku presentation to show off the idea that you can spawn up a lot of Go routines in Go and have it run efficiently.  I did this as a test in Clojure to see how far you could push this with JVM threads.

In the Chinese Whispers example, a daisy-chain of go routines are formed and they communicate unidirectionally along a series of Go channels.  Go routine A signals to B who signals to C, etc.  The message passed along the way is an integer than is incremented on each hand off.

With the Go example (which you can get in the go-examples directory), you can run a daisy-chain of 100,000 go routines very efficiently:

    $ time ./chinese-whispers 
    100001
    
    real    0m0.249s
    user    0m0.136s
    sys     0m0.104s

100,000 go routines just spun up, passed the integer baton to one another and the last one printed out the result in .25 seconds.  Pretty amazing.

On my Linux Xubuntu system running Java 7, I could only get to a little north of 20,000 threads (go-lightly routines) until my system ran out of native threads.  Here are the simple benchmarks:

    user=> (time (w/whispers-main 20000))
    20001
    "Elapsed time: 434.828855 msecs"
    
    user=> (time (w/whispers-main 10000))
    10001
    "Elapsed time: 260.610251 msecs"


I did write a modified version that doesn't create all the go routines up front and then churn through them like a set of dominoes.  Instead we create the dominoes (go-routines) as you go.  In that case, I was able to do more than 200,000 go-lightly routines before running out of JVM resources, but it's also nearly 10 times slower (comparing the 20,000 routines examples between the two Clojure implementations):

    user=> (time (w/whispers-as-you-go 20000))
    20001
    "Elapsed time: 3285.003332 msecs"

    user=> (time (w/whispers-as-you-go 100000))
    100001
    "Elapsed time: 4329.229876 msecs"
    
    user=> (time (w/whispers-as-you-go 200000))
    200001
    "Elapsed time: 8800.131523 msecs"
    

You may want to compare it to some other examples in other languages here:  https://cxwangyi.wordpress.com/2012/07/29/chinese-whispers-in-racket-and-go/

**How to run**

From the REPL:

- see examples above

From the command line, spawning 100 workers, 300 requesters and running until 2000 requests are processed

    $ lein run :whispers 500
    501
    $ lein run :whispers-as-go 500
    501

----

<a name="webcrawler"></a>
### web crawler

This example comes from the O'Reilly Clojure Programming book. At the end of chapter 4, the authors created a simple web crawler to show a fairly involved example using agents.

The go-lightly example implements the same functionality using go routines and Go channels.

**How to run**

The program can take up to three optional args
* arg1: number of crawler go threads (defaults to 1)
* arg2: duration (in millis) to run crawling (defaults to 2000)
* arg3: initial url to crawl (defaults to http://golang.org/ref/)


From the REPL:

    user=> (require '[thornydev.go-lightly.examples.webcrawler.webcrawler :as wc])
    nil
    user=> (wc/-main 8 2500)
    ------------------------------
    url-channel: 2450
    freqs-channel: 0
    status-channels: #<Channel <=[] >
    word-frequencies: 3583
    crawled-urls: 26
    ------------------------------

From the command line, spawning 100 workers, 300 requesters and running until 2000 requests are processed

    $ lein run :webcrawler 8 2500
    ------------------------------
    url-channel: 2389
    freqs-channel: 0
    status-channels: #<Channel <=[] >
    word-frequencies: 3579
    crawled-urls: 26
    ------------------------------
