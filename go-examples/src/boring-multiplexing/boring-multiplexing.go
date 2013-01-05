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

// (2) Fan-in
func boring(msg string) <-chan string { // Return receive-only channel of strings
    c := make(chan string)
    go func() { // We launch goroutine from inside the function
        for i := 0; ; i++ {
            c <- fmt.Sprintf("%s %d", msg, i)
            time.Sleep(time.Duration(rand.Intn(1000)) * time.Millisecond)
        }
    }()
    return c
}

func fanIn(input1, input2 <-chan string) <-chan string {
    c := make(chan string)
    go func() { for { c <- <-input1 } }()
    go func() { for { c <- <-input2 } }()
    return c
}

func main() {
	rand.Seed(time.Now().UnixNano())
	c := fanIn(boring("Joe"), boring("Ann"))
	for i := 0; i < 10; i++ {
		fmt.Println(<-c)
	}
	fmt.Println("You're both boring: I'm leaving.")
}
