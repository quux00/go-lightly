
# Introduction to go-lightly

### todo

* Go range == Clojure while: http://golangtutorials.blogspot.com/2011/06/channels-in-go-range-and-select.html
X Add more type hinting to core
* Finish blog comparing webcrawler implementations
O Finish revised README
X Tag existing GitHub repo and publish that tag on blog entries 1-5
* Benchmark webcrawler-interop vs. webcrawler-typedChannels
X jvisualvm analysis of Clojure chinese whispers example
X Publish TypedChannels version to GitHub
O Learn how to package for and publish to Clojars
* Restructure go-lightly into go-lightly and go-lightly-examples lein projects?
* Publish answer on Stackoverflow about doing Go concurrency in Clojure: link to go-lightly TypedChannel version out
* Fix font on blogs (color older entries and get the font face correct so looks right on Windows)
X Start go-lightly wiki on GitHub to see how it works and how to link to it from the README
* Read: man select_tut
* Read source code of Clojure case macro
* Add selectf: options:'

(defn macro-version []
  (selectf
   ch1 #(println %)
   ch2 #(println %)
   :default #(println "nada")))

(defn data-all-the-things-version []
  (selectf
   [ch1 #(println %)]
   [ch2 #(println %)]
   [:default #(println "nada")]))


### Criterium benchmarks
* google-1 benchmarks
 
    Evaluation count : 420 in 60 samples of 7 calls.
                 Execution time mean : 147.473263 ms
        Execution time std-deviation : 19.861213 ms
       Execution time lower quantile : 104.152900 ms ( 2.5%)
       Execution time upper quantile : 186.641395 ms (97.5%)

* google-2f benchmarks

    Evaluation count : 960 in 60 samples of 16 calls.
                 Execution time mean : 75.155186 ms
        Execution time std-deviation : 4.694719 ms
       Execution time lower quantile : 66.447248 ms ( 2.5%)
       Execution time upper quantile : 83.627739 ms (97.5%)

* google-2c benchmarks

    Evaluation count : 780 in 60 samples of 13 calls.
                 Execution time mean : 74.443997 ms
        Execution time std-deviation : 4.788948 ms
       Execution time lower quantile : 64.132246 ms ( 2.5%)
       Execution time upper quantile : 82.208824 ms (97.5%)


* google-2.0 benchmarks: typed

    Evaluation count : 960 in 60 samples of 16 calls.
                 Execution time mean : 75.600093 ms
        Execution time std-deviation : 4.939574 ms
       Execution time lower quantile : 64.870360 ms ( 2.5%)
       Execution time upper quantile : 84.254095 ms (97.5%)

* google-3.0-nt benchmarks: typed

    ;; with buffered-channel in first-to-finish
    Evaluation count : 1260 in 60 samples of 21 calls.
                 Execution time mean : 54.121599 ms
        Execution time std-deviation : 4.514020 ms
       Execution time lower quantile : 44.190562 ms ( 2.5%)
       Execution time upper quantile : 61.165908 ms (97.5%)

    ;; with sync-channel in first-to-finish
    Evaluation count : 1320 in 60 samples of 22 calls.
                 Execution time mean : 53.796138 ms
        Execution time std-deviation : 4.069949 ms
       Execution time lower quantile : 47.272136 ms ( 2.5%)
       Execution time upper quantile : 61.356316 ms (97.5%)

    ;; with sync-channel in first-to-finish
    Evaluation count : 1380 in 60 samples of 23 calls.
                 Execution time mean : 54.083988 ms
        Execution time std-deviation : 4.317564 ms
       Execution time lower quantile : 45.592715 ms ( 2.5%)
       Execution time upper quantile : 61.786262 ms (97.5%)


### chinese-whispers benchmarks

    user=> (time (w/whispers-as-you-go 200000))
    200001
    "Elapsed time: 8800.131523 msecs"
    
    user=> (time (w/whispers-as-you-go 100000))
    100001
    "Elapsed time: 4329.229876 msecs"
    
    user=> (time (w/whispers-as-you-go 100000))
    100001
    "Elapsed time: 4472.125846 msecs"
    
    user=> (time (w/whispers-as-you-go 20000))
    20001
    "Elapsed time: 3285.003332 msecs"
    
    user=> (time (w/whispers-main 20000))
    20001
    "Elapsed time: 434.828855 msecs"
    
    user=> (time (w/whispers-main 10000))
    10001
    "Elapsed time: 260.610251 msecs"


## Notes on Lamina

http://ideolalia.com/110624930

### Notes from Tellman StrangeLoop presentations
* http://www.infoq.com/presentations/Event-Driven-Programming-in-Clojure
* Tellman proposes handling async programming with an "event-driven data structure"
* In a fork-join scenario, the join assumes the task will complete. This differs from Pike's go-concurrency examples
* Threads are not free and we address that by creating Thread pools
 * If I'm going to create my own daemon-future threads, to be robust it would need to use a Thread pool (or would it?)


### Questions for Zach / Lamina Google group

I'm new to lamina.  I've been reading about it and listened to Zach's StrangeLoop presentation and have a couple questions.

First I realize that the primary use case for channels is an event queue, which likely means you want it to be unbounded and non-blocking. Thus, lamina uses a ConcurrentLinkedQueue underneath.

But I've been playing with lamina as a tool to implement the concurrency constructs in Go (the language).

* Is there a way to constrain the size of a channel, such that an attempt to enqueue a "full" channel will block?  I don't want to close the channel, I just want to constrain it's size - it can have values (events) moving through it, but cannot ever be large than size N.  This could be via the LinkedBlockingQueue, for example.  If no, is there another way to apply "backpressure" on the data/events coming into the channel?

* Is there a way to use a ResultChannel as a queue of 1?  Or once it's realized it is "stuck" and cannot be used for anything besides reading that value (akin to a promise).
==> NO, don't ask

* There is a relatively undocumented `named-channel` fn - what is the value of a named channel?  What use case would it be good for? 

* How is the broadcast-channel not an infinite loop?
  * I get: StackOverflowError   java.util.concurrent.locks.ReentrantLock.unlock (ReentrantLock.java:460)

* This piece of documentation is misleading:
  * A single channel can be useful on its own, when used as an asynchronous variant of a LinkedBlockingQueue, but most of the time we don’t want to handle each message individually.
  * From: https://github.com/ztellman/lamina/wiki/Channels-new


* How does join work?  See diagram in the Tellman blog entry
  * See k1-main3 of kaychayev's "boring" examples
  * (join ch1 ch2)  => ch2 will be the destination for messages to ch1
  * All messages enq'd into ch1 will immediately be drained to ch2 =>
  * it is basically the right way to do: (def ch2 (map* identity ch1))


## Ideas
### Another thing to look into
* http://docs.oracle.com/javase/1.5.0/docs/api/java/util/concurrent/ExecutorCompletionService.html
 * recommended here: http://stackoverflow.com/questions/11298961/equivalent-of-goroutines-in-clojure-java
 * will this be useful to this endeavor?
  * Reviewed: The ExecutorCompletionServer is will help you get the next available result from a pool of Futures, but it cannot be a general purpose Go channel, bcs it can only return one result per future - it is constrained by the mechanics of how a future works
### useing channels or selects on channels in HOFs like map, reduce, filter, etc.
 * (channel->lazyseq ch)
 * (map inc (channels->lazyseq ch1 ch2 ch3)  (does a select each iteration)
 * (lazy-select ch1 ch2 ch3)  ;; what is the end point? closed channel? or allow infinite seqs?
 * 

### agents as go routines?
 * while agents are data wrappers, send takes a function. agents are multiplexed onto threads particurly when called with send-off.  Would this be a way to emulate go routine multiplexing?  Or once they are on a thread (in a send routine), do they hold that thread until finished and thus are no better than a future or daemon Thread??

### how do you pause a go-routine?
 * you would give it reference to a wait or pause channel and send a message on it
 * how would you unpause it?  send another message on that channel telling it to resume - it would block on that wait channel waiting for a resume msg

### add meta-data :prefer to prefer or priotize the channel in a go/select clause => note that meta-data is tricky -> has to be applied to the value not the Var and to apply it to a value it must implement IObj, so another reason to create a defrecord/deftype for the channel types.

Notes on meta-data:

    user=> (defn show-meta [v] (println (meta v)))
    #'user/show-meta
    user=> (def ^:prefer ch (channel))
    #'user/ch
    user=> (show-meta ch)
    nil
    nil
    user=> (show-meta #'ch)
    {:prefer true, :ns #<Namespace user>, :name ch, :line 1,
     :file NO_SOURCE_PATH}
    user=> (def ch1 (with-meta #{(channel)} {:prefer true}))
    #'user/ch1
    user=> (show-meta ch1)
    {:prefer true}
    nil
    user=> ch1
    #{#<LinkedTransferQueue []>}


## Go channels
http://golang.org/ref/spec

Operations on go channels: send and receive

## Go examples
tinyurl.com/gochatroulette
tinyurl.com/goloadbalancer
tinyurl.com/gosieve
tinyurl.com/gopowerseries



## Racket equiv of Go select
* https://cxwangyi.wordpress.com/2012/07/29/chinese-whispers-in-racket-and-go/
* http://docs.racket-lang.org/reference/sync.html#(def._((quote._~23~25kernel)._sync))


## ----- Notes for blog entries ----- ##
* from the channel webcrawler example: almost none of the fns are referentially transparent or testable in isolation :-(


Go channels are strongly typed – you can only pass a single type of value over a channel.




## Go talks
* Google IO 2010 talk (Load Balancer example): https://www.youtube.com/watch?v=jgVhBThJdXc



## Notes on agents and webcrawler example
* Usage splits into two forms:
  * blocking => send-off
  * non-blocking => send 
  
Reading from a queue can be a blocking operation.
Processing each message is CPU bound 

For a series of slow/blocking operations, don't want to be sending to the same agent, as they will queue up (or have to retry when one works before it(?)).  In such as a case, need one agent per operation.

Three pools of state to webcrawler example
1. URLs yet to be crawled (in a BlockingQueue) => would need to be a Go BufferedChannel
2. All the links on a page: "crawled-urls".  Kept in set in atom.
3. Text of each page, on which count word freqs.  Kept in map in atom.  Map is words => counts.



## Notes on channel types and actions

fn                channel        buffered-channel
------            -------        ----------------
put              .transfer       .put (blocks only if full)
take             .take           .take (blocks only if empty) 
peek             .peek           .peek
select-timeout   .poll timeout   .poll timeout  (if only one channel passed)
select-nowait    .poll           .poll
size             .size           .size



## Reading to do
https://groups.google.com/forum/?hl=fr&fromgroups=#!topic/golang-nuts/koCM3i-bbMs
* Notes from above
** Synchronous channels have a lot of advantages by making program flow predictable and easier to think about.
