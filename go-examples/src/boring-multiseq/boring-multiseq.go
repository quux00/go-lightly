package main;

import (
	"fmt"
	"math/rand"
    "time"
)

// Channels-driven concurrency with Go
// Code examples from Rob Pike's talk on Google I/O 2012:
// http://www.youtube.com/watch?v=f6kdp27TYZs&feature=youtu.be
//
// Concurrency is the key to designing high performance network services.
// Go's concurrency primitives (goroutines and channels) provide a simple and efficient means
// of expressing concurrent execution. In this talk we see how tricky concurrency
// problems can be solved gracefully with simple Go code.

// (3) Restoring sequencing

type Message struct {
    str string
    wait chan bool
}

func boring(msg string) <-chan Message { // Return receive-only channel of strings
	waitForIt := make(chan bool) // Shared between all messages.
    c := make(chan Message)
    go func() { // We launch goroutine from inside the function
        for i := 0; ; i++ {
            c <- Message{ fmt.Sprintf("%s: %d", msg, i), waitForIt }
            time.Sleep(time.Duration(rand.Intn(1e3)) * time.Millisecond)
            <-waitForIt
        }
    }()
    return c
}

func fanIn(input1, input2 <-chan Message) <-chan Message {
    c := make(chan Message)
    go func() { for { c <- <-input1 } }()
    go func() { for { c <- <-input2 } }()
    return c
}

func main() {
	rand.Seed(time.Now().UnixNano())
	c := fanIn(boring("Joe"), boring("Ann"))
	
	// Each speaker must wait for a go-ahead
	for i :=0; i < 5; i++ {
		msg1 := <-c; fmt.Println(msg1.str)
		msg2 := <-c; fmt.Println(msg2.str)
		msg1.wait <- true
		msg2.wait <- true
	}
	fmt.Println("You're both boring: I'm leaving.")
}
