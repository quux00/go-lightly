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
// This code adapted from Alexey Kacheyev's transcription
// of Pike's code: https://gist.github.com/3124594
// problems can be solved gracefully with simple Go code.
// (4) Select control structure

func boring(msg string) <-chan string {
    c := make(chan string)
    go func() {
        for i := 0; ; i++ {
            c <- fmt.Sprintf("%s %d", msg, i)
            time.Sleep(time.Duration(rand.Intn(1200)) * time.Millisecond)
        }
    }()
    return c
}

func fanIn(input1, input2 <-chan string) <-chan string {
    c := make(chan string)
    go func() {
        for {
            select {
            case s := <-input1: c <-s
            case s := <-input2: c <-s
            }
        }
    }()
    return c
}

func fanInWithSelect() {
    c := fanIn(boring("Joe"), boring("Ann"))
    for i := 0; i < 10; i++ {
        fmt.Println(<-c)
    }
    fmt.Println("You're both boring: I'm leaving.")
}

func timeoutPerRound() {
    c := boring("Joe")
    for {
        select {
        case s := <-c:
            fmt.Println(s)
        case <-time.After(1 * time.Second):
            fmt.Println("You're too slow!")
            return
        }
    }
}

// Timeout for whole conversation using select
func timeoutWholeConversation() {
    c := boring("Joe")
    timeout := time.After(1 * time.Second)
    for {
        select {
        case s := <-c:
            fmt.Println(s)
        case <-timeout:
            fmt.Println("You talk too much!")
            return
        }
    }
}

func main() {
    rand.Seed(time.Now().UnixNano())
    if (len(os.Args) != 2) {
        fmt.Println("Usage: boring-select [round|whole|fanIn]")
    } else if (os.Args[1] == "round") {
        timeoutPerRound()
    } else if (os.Args[1] == "whole"){
        timeoutWholeConversation()
    } else {
        fanInWithSelect()
    }
}
