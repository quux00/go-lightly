package main;

import (
    "fmt"
    "math/rand"
    "time"
    "os"
)

// Channels-driven concurrency with Go
// Code examples from Rob Pike's talk on Google I/O 2012:
// http://www.youtube.com/watch?v=f6kdp27TYZs&feature=youtu.be
//
// This gist is modified based on Alexey Kachayev's gist transcriptions
// of Pike's code: https://gist.github.com/3124594
// I cleaned them up a bit so that that will compile
// and run with the go compiler and runtime.

// (1) Generator: function that returns the channel
func boring(msg string) <-chan string { // Return receive-only channel of strings
    c := make(chan string)
    go func() { // We launch goroutine from inside the function
        for i := 0; ; i++ {
            c <- fmt.Sprintf("%s %d", msg, i)
            time.Sleep(time.Duration(rand.Intn(1e3)) * time.Millisecond)
        }
    }()
    return c
}

func singleGenerator() {
    c := boring("boring!") // Function returning a channel.
    for i := 0; i < 5; i++ {
        fmt.Printf("You say: %q\n", <-c)
    }
    fmt.Println("You're boring: I'm leaving.")
}

func multipleGenerators() {
    joe := boring("Joe")
    ann := boring("Ann")

    for i := 0; i < 10; i++ {
        fmt.Println(<-joe)
        fmt.Println(<-ann)
    }
    fmt.Println("You're boring: I'm leaving.")
}

// More instances
func main() {
	rand.Seed(time.Now().UnixNano())

    if (len(os.Args) != 2) {
        fmt.Println("Usage: boring-generator [single|multiple]")
    } else if (os.Args[1] == "single") {
        singleGenerator()
    } else {
        multipleGenerators()
    }
}
