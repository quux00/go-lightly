package main

// Copyright Alexey Kachayev
// https://gist.github.com/4634932

import (
  "fmt"
  "math/rand"
  "time"
)

const (
  CUTTING_TIME   = 20
  NUM_BARBERS    = 1
  NUM_HALL_SEATS = 3
)

type Barber struct {
  val int
}

type Client struct {
  val int
}

func main() {
  clients := make(chan *Client)
  go clientProducer(clients)
  go BarberShop(clients)
  time.Sleep(2 * time.Second)
}

func clientProducer(clients chan *Client) {
  for {
    time.Sleep(time.Duration(rand.Intn(28)+7) * time.Millisecond)
    clients <- &Client{}
  }
}

func cutHair(barber *Barber, client *Client, finished chan *Barber) {
  // Cutting hair
  time.Sleep(CUTTING_TIME * time.Millisecond)
  finished <- barber
}

func BarberShop(clients <-chan *Client) {
  freeBarbers := []*Barber{}
  waitingClient := []*Client{}
  syncBarberChan := make(chan *Barber)

  //creating barbers
  for i := 0; i < NUM_BARBERS; i++ {
    freeBarbers = append(freeBarbers, &Barber{})
  }

  for {
    select {
    case client := <-clients:
      if len(freeBarbers) == 0 {
        if len(waitingClient) < NUM_HALL_SEATS {
          // client is waiting in the hall
          waitingClient = append(waitingClient, client)
          fmt.Printf("Client is waiting in hall (%v)\n", len(waitingClient))
        } else {
          // hall is full - bye-bye client
          fmt.Println("No free space for client")
        }
      } else {
        barber := freeBarbers[0]
        freeBarbers = freeBarbers[1:]
        fmt.Println("Client goes to barber")
        go cutHair(barber, client, syncBarberChan)
      }
    // barber finish work
    case barber := <-syncBarberChan:
      if len(waitingClient) > 0 {
        // get client from hall
        client := waitingClient[0]
        waitingClient = waitingClient[1:]
        fmt.Printf("Take client from room (%v)\n", len(waitingClient))
        go cutHair(barber, client, syncBarberChan)
      } else {
        // barber is going to sleep
        fmt.Println("Barber idle")
        freeBarbers = append(freeBarbers, barber)
      }
    }
  }
}
