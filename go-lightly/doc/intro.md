# Introduction to go-lightly

See README at top level of project


## Cookbook

### Two threads/routines need to synchronize and communicate

Use a single synchronous channel so one can wait on the other.

One can wait for the other to send a message:

(def ch (go/channel))
(go/go (do (lots-of-work)
           (go/put ch :done)))
;; blocks until go routine puts value on the channel
(when (= :done (go/ch take))
  (println "synchronized and done"))


Or one can wait for the other to receive its message

(def ch (go/channel))
(go/go (do (lots-of-work)
           (println (go/take ch))))
;; blocks until go routine puts takes the value off the channel
(go/put ch :done)


Use a  synchronous channel so one can wait on the other.
