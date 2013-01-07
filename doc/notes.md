# Introduction to go-lightly

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


## Notes for blog entries




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



