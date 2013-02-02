#### Setting preferred status on a channel
Unlike the Go model, there is a way in go-lightly to elevate the status of a channel to be read preferentially over other channels.  To the `select` statement there are two categories: preferred and non-preferred.  Preferred channesl will be read first over non-preferred.  If no values exist on a preferred channel, then it will read from non-preferred channels.

If two or more preferred channels have values ready, then it will choose randomly between those preferred channels.  TimeoutChannels are preferred by default so that timeout sentinel messages are captured as soon as possible.

To set a channel as preferred you can either use the `preferred-channel` factory method or call `prefer!` on the channel.

    user=> (doc go/prefer!)
    -------------------------
    thornydev.go-lightly.core/prefer!
    ([channel])
      Modifies the channel to have preferred status in a select
      statement, so it will be preferentially read from over a
      non-preferred channel if multiple channels have values ready

Here's a REPL session demonstrating this:

```clj
user=> (def ch1 (go/channel))  (def bch2 (go/channel 10))  (def bch3 (go/channel 10))
#'user/ch1
#'user/bch2
#'user/bch3
user=> (go/go (go/put ch1 :ch1-1))
#<core$future_call$reify__6110@108c4c0a: :pending>
user=> (dotimes [i 3] (go/put bch2 (str ":bc2-" i)) (go/put bch3 (str ":bc3-" i)))
nil
user=> (pprint [ch1 bch2 bch3])
[#<Channel <=[ ...(:ch1-a)] >
 #<BufferedChannel <=[:bc2-0 :bc2-1 :bc2-2] >
 #<BufferedChannel <=[:bc3-0 :bc3-1 :bc3-2] >]
nil
;; set channel-2 as preferred so its values will be read
;; by select before the non-preferred channels 
user=> (go/prefer! bch2)
#<BufferedChannel <=[] >
user=> (dotimes [i 7] (println (go/select ch1 bch2 bch3)))
:bc2-0
:bc2-1
:bc2-2
:bc3-0
:bc3-1
:ch1-1
:bc3-2
```




## select

Go comes with a ready-made control structure called select. It provides a shorthand way to specify how to deal with multiple channels, as well as allow for timeouts and non-blocking behavior (via a "default" clause). It looks like a switch/case statement in Go, but is different in that all paths involving a channel are evaluated, rather than just picking the first one that is ready.

The select statement is a central element in writing idiomatic Go concurrent applications.  In fact, Rob Pike, in his [Google IO 2012 presentation](http://www.youtube.com/watch?v=f6kdp27TYZs&feature=youtu.be)) on Go said:

> "The select statement is a key part of why concurrency is built into Go as features of the language, rather than just a library. It's hard to do control structures that depend on libraries."

Let's look at an example:

```go
select {
case v1 := <-c1:
    fmt.Printf("received %v from c1\n", v1)
case v2 := <-c2:
    fmt.Printf("received %v from c2\n", v2)
}
```

This `select` evaluates two channels and there are four possible scenarios:

1. c1 is ready to give a message, but c2 is not. The message from c1 is read into the variable v1 and the code clause for that first case is executed.
2. c2 is ready to give a message, but c1 is not. v2 then is assigned to the value read from c2 and its code clause is executed.
3. Both c1 and c2 are ready to give a message. One of them is randomly chosen for reading and execution of its code block. Note this means that you cannot depend on the order your clauses will be executed in.
4. Neither c1 nor c2 are ready to give a message. The select will block until the first one is ready, at which point it will be read from the channel and execute the corresponding code clause.

`select` statements in Go can also have timeouts or a default clause that executes immediately if none of the other cases are ready.  Examples are provided in [the wiki](https://github.com/midpeter444/go-lightly/wiki).

### select in go-lightly

The go-lightly library provides select-like functionality, but not exactly like Go does.  At present go-lightly's select is a function (or rather a suite of functions), not a control structure.  However, a control structure version could certainly be written if it is warranted.

The select function is go-lightly is modeled after the `sync` function in the [Racket language](http://racket-lang.org/) (discussed in [one of my go-lightly blog entries](http://thornydev.blogspot.com/2013/01/go-concurrency-constructs-in-clojure2.html) in more detail).  You pass in one or more channels and/or buffered channels and it performs the same logic outlined above to return the value from the next channel when it is available.

```clj
(def ch (go/channel))
(def buf-ch1 (go/channel 100))
(def buf-ch2 (go/channel Integer/MAX_VALUE))

(let [msg (go/select ch buf-ch1 buf-ch1)]
  ;; do something with first message result here
  )
```

If you don't want to block until a channel has a value, use `select-nowait`, which takes an optional return sentinel value if no channel is ready for reading.  It returns `nil` if no channel is ready and no sentinel return value is provided.

```clj
user=> (select-nowait ch1 ch2 ch3 :bupkis)
:bupkis
user=> (select-nowait ch1 ch2 ch3)
nil
```

You can also use `select-timeout` for a select with a specified timeout value.  See the #timeouts section for details.

Finally, you can also set one or more channels as "preferred" so that select will preferentially read from them over non-preferred channels.  This is not a feature of Go's select.

In that case the logic for select in go-lightly changes to:

1. Evaluate all preferred channels.
  1. If only one is ready, `take` from it and return its value
  2. If more than one is ready, choose randomly among them, `take` from it and return its value
2. Evaluate all non-preferred channels.
  1. If only one is ready, `take` from it and return its value
  2. If more than one is ready, choose randomly among them, `take` from it and return its value  
3. If none is ready, then:
  1. If called with `select`, wait a short sleep and go to step 1 above
  1. if called with `select-nowait` return nil immediately 
  1. if called with `select-timeout` return nil if has timed out, otherwise do short sleep and return to step 1 above


Note that a TimeoutChannel is a preferred channel by default.  See the #status section for more details on preferred.
