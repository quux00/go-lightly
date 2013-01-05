## Go examples in Go

Many of examples from Rob Pike's Google IO 2012 talk are here as well as some he referenced, such as the elegnta concurrent prime sieve example using Go channels and Go routines.

You can look at these to compare them to the Clojure versions I've written and see if you agree how to "translate" or "transpile" them into Clojure.

## Usage

If you don't have Go installed, see the [golang install guide](http://golang.org/doc/install).  On Ubuntu, it is as simple as:

    sudo apt-get install golang-go

Next, decide where you want your go projects to live (mine are in `$HOME/lang/go/projects`).  cd to that directory and do:

    $ export GOPATH=/path/to/go-lightly/go-examples  # this may not be required
    $ cd boring-generators    # or whichever one you want
    $ go build   # this invokes the compiler
    
You will then have a `boring-generators` executable that you can run:

    $ ./boring-generators
    
(*Note:* I didn't find that setting GOPATH was really necessary, but it is in the instructions, so YMMV).

