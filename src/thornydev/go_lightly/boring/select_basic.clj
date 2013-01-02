(ns thornydev.go-lightly.boring.select-basic
  (:require [thornydev.go-lightly.util :refer [with-channel-open]])
  (:use lamina.core))

(select ch1 ch2)
(select-timeout 1000 ch1 ch2)

(select
 ch1 #(println out)
 ch2 #(println out)
 timeout #(println "You suck")
 :default #(println "Leaving"))

(case str
  "hi" )
