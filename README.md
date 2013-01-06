# go-lightly

A foray into implementing the concurrency constructs of the Go language in Clojure, using what Clojure provides out of the box, what Java provides through interop, existing Clojure libraries and whatever else I need to construct to make it work.

This is in the very early stages of exploration and I'm documenting that exploration a series of blog posts.

* Part 1: http://thornydev.blogspot.com/2013/01/go-concurrency-constructs-in-clojure.html
* Part 2: http://thornydev.blogspot.com/2013/01/go-concurrency-constructs-in-clojure2.html

## Structure

The go-lightly namespace has only one library piece so far: the thornydev.go-lightly.core namespace that defines helper macros and functions.

The rest is a series of example implementations based on talks by Rob Pike.  (See the clj-examples directory.)

Talks by Rob Pike:
* [Google I/O 2012 - Go Concurrency Patterns](http://www.youtube.com/watch?v=f6kdp27TYZs&feature=youtu.be)
* [Concurrency is not Parallelism](http://vimeo.com/49718712) ([slides here](https://rspace.googlecode.com/hg/slide/concur.html#landing-slide))

The concurrent-primes-sieve is based on this example in Go: http://tinyurl.com/gosieve


## Dependencies and input from others

The Clojure [lamina](https://github.com/ztellman/lamina) library is required for the examples that use a lamina channel to emulate a Go channel.

[Alexey Kachayev](https://github.com/kachayev) wrote down the Go code that Pike used in the 2012 Google IO presentation, which doesn't seem to have been made available.  Alexey published them as gists.  They won't compile out of the box, so I've been modifying them, but wanted to link to his gists:  https://gist.github.com/3124594.

Alexey also then brainstormed on ways to implement these examples in Clojure using the lamina library.  Those gists are at: https://gist.github.com/3146759

I also copied many of Alexey's examples and modified them to get them to run here - in the go-examples and clj-examples directories.  I'm still studying them, so may merge more of his code into this project.

## Usage

Each example can be loaded up in the REPL and run that way.

Because I want to make sure all of these will run and end gracefully (not hang), I also set up a massive case statement in go-lightly.core/-main method to run any of these via `lein run`.  You can run any number of them by using the defined keyword.

Example:

    $ lein run :gen-amp :gen-lam1 :google-3-alpha

will run all three of those targets sequentially.

## License

Copyright © 2012 Michael Peterson (and Alexey Kachayev where noted)

Distributed under the Eclipse Public License, the same as Clojure.
