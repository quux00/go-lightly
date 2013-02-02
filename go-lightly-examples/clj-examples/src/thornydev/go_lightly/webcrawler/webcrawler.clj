(ns thornydev.go-lightly.webcrawler.webcrawler
  (:require [thornydev.go-lightly.core :as go]
            [net.cgrand.enlive-html :as enlive]
            [clojure.java.io :refer [as-url]]
            [clojure.string :refer [lower-case]])
  (:import (java.io StringReader IOException)
           (java.net URL MalformedURLException)
           (java.util.concurrent CountDownLatch)))

;; A simple webcrawler based on the example from the end of Chapter 4
;; of the O'Reilly Clojure Programming book.  This version attempts
;; to more heavily use concurrency queues (channels) than the book
;; example to compare approaches.

;; As with the book example, this web crawler is ill-behaved in not
;; throttling connections, so use sparingly for short bursts of testing.

;; ------------------------- ;;
;; ---[ data structures ]--- ;;
;; ------------------------- ;;

;; a buffered channel to allow a large number of urls to crawl
(def url-channel (go/channel Integer/MAX_VALUE))

;; holds maps of word freqs after parsing an individual page
(def freqs-channel (go/channel Integer/MAX_VALUE))

;; status channels for the master thread to stop messages onto
(def crawler-status-channel (go/channel))
(def freq-reducer-status-channel (go/channel))

;; set of urls already crawled (ensure no dups searched)
(def crawled-urls (agent #{}))


;; ------------------- ;;
;; ---[ functions ]--- ;;
;; ------------------- ;;

;; ---[ crawler section ]--- ;;

(defn links-from
  "Returns a lazy-seq of URLs from parsing a web page"
  [base-url html]
  (remove nil? (for [link (enlive/select html [:a])]
                 (when-let [href (-> link :attrs :href)]
                   (try
                     (URL. base-url href)
                                        ; ignore bad URLs
                     (catch MalformedURLException e))))))

(defn words-from
  "Returns a lazy-seq of words from parsing a web page"
  [html]
  (let [chunks (-> html
                   (enlive/at [:script] nil)
                   (enlive/select [:body enlive/text-node]))]
    (->> chunks
         (mapcat (partial re-seq #"\w+"))
         (remove (partial re-matches #"\d+"))
         (map lower-case))))

(defn process-url
  "Takes a url (as a string) and:
    Marks the url a crawled (in the crawled-urls agent).
    Downloads the page, parses the links from it and puts
     links not already seen onto the main url-channel
    Counts the word frequencies on that page into a map and puts
     that map onto the freqs-channel.
   Returns void."
  [^String urlstr]
  (try
    (send-off crawled-urls conj urlstr)
    (let [html (enlive/html-resource
                (StringReader. (slurp urlstr)))
          links (links-from (as-url urlstr) html)]
      (doseq [lnk links :let [nu (str lnk)]]
        ;; only put on todo list if haven't seen yet
        (when-not (@crawled-urls nu)
          (go/put url-channel nu)))
      
      (->> html
           words-from
           (reduce (fn [m word] (update-in m [word] (fnil inc 0))) {})
           doall
           (go/put freqs-channel)))
    (catch IOException io) ;; ignore Pushback buffer overflows from tag-soup lib
    (catch InterruptedException intex) ;; forcibly shut down via future-cancel
    (catch Exception e (println "process-url ERROR: " e))))

(defn crawl
  "Main loop for crawlers. Should be called as a go-lightly routine/thread.
   In each loop it checks for a shut down msg on the crawler-status-channel
   and grabs the CountDownLatch out of that msg and decrements its count."
  []
  (try
    (loop []
      (process-url (go/take url-channel))
      (let [msg (go/select-nowait crawler-status-channel)]
        (if (nil? msg)
          (recur)
          (.countDown (:latch msg)))))
    (catch Exception e (println "crawl ERROR: " e))))

(defn start-crawlers
  "Takes a number of crawlers to start and spawns them
   in go-lightly routines."
  [ncrawlers]
  (dotimes [_ ncrawlers]
    (go/go (crawl))))


;; ---[ frequency reducer section ]--- ;;

(defn freq-cycle
  "Grabs the next map value off freq-channel, merges it with the
   master map (msubtots) and returns an updated map."
  [msubtots]
  (if-let [freqs (go/select-timeout 100 freqs-channel)]
    (merge-with + msubtots freqs)
    msubtots)
  )

(defn drain-freqs-channel
  "Drains all the remaining values (maps) it can get off the freqs-channel
   in one swoop and merges those counts into the +msubtots+ map passed in.
   Returns a revised word frequency map."
  [msubtots]
  (->> msubtots
       (conj (vec (go/drain freqs-channel)))
       (apply merge-with +)))

(defn reduce-freqs
  "The main loop for a word frequency reducer, which reads word freq maps
   put onto the freqs-channel and reduces them into a single master
   word master map.  When a stop message comes in on the
   freq-reducer-status-channel, it grabs whatever is left on the freqs-
   channel, and returns it on the channel in the message from the status
   channel, then shutting down."
  [id]
  (try
    (loop [freqtots {}]
      (let [msg (go/select-nowait freq-reducer-status-channel)]
        (if-not (nil? msg)
          (go/put (:channel msg) freqtots)
          (recur (freq-cycle freqtots)))))
    (catch Exception e
      (println "reduce-freqs ERROR" e)
      (println (.printStackTrace e)))))

(defn start-frequency-reducer
  "Spawns a go-lightly routine for one frequency reducer that will process
   the word frequency maps put onto the freqs-channel by the crawlers."
  [id]
  (go/go (reduce-freqs id)))


;; ---[ main control section ]--- ;;

(defn stop-crawlers
  "Signal all the crawlers to stop. The message to the crawlers includes a
   CountDownLatch. Wait (with a scaled timeout) for the latch to cound down
   to zero before proceeding."
  [ncrawlers]
  (go/with-timeout (min 2500 (* 660 ncrawlers))
    (let [latch (CountDownLatch. ncrawlers)]
      (dotimes [_ ncrawlers]
        (go/put crawler-status-channel {:msg :stop, :latch latch}))
      (.await latch))))

(defn stop-frequency-reducer-and-get-result
  "Signal the frequency-reducer to stop. The message to the reducer includes
   a go-lightly channel for the reducer to message back on when it finishes.
   This fn waits (with 2 sec timeout) for the reducer to message back."
  []
  (go/with-timeout 2000
    (let [result-channel (go/channel)]
      (go/put freq-reducer-status-channel {:msg :stop
                                           :channel result-channel})
      (go/take result-channel))))

(defn init
  "Initialize the data structures. Needed for repeated use in a REPL."
  [urlstr]
  (go/clear url-channel)
  (go/put url-channel urlstr)
  (go/clear freqs-channel)
  (go/clear freq-reducer-status-channel)
  (go/clear crawler-status-channel)
  (if (agent-error crawled-urls)
    (restart-agent crawled-urls #{} :clear-actions true))
  (send crawled-urls (fn [_] #{}))
  (when-not (await-for 1000 crawled-urls)
    (throw (IllegalStateException.
            "Unable to reinitialize crawled-urls agent"))))

(defn start [ncrawlers]
  (start-crawlers ncrawlers)
  (start-frequency-reducer :freq-reducer))

(defn report [word-freqs]
  (println "------------------------------")
  (println "url-channel:" (.size url-channel))
  (println "freqs-channel:" (.size freqs-channel))
  (println "status-channels:" freq-reducer-status-channel)
  (println "word-frequencies:" (count word-freqs))
  (println "crawled-urls:" (count @crawled-urls))
  (println "------------------------------"))

(defn parse-args
  "arg1: number of crawler go threads
   arg2: duration to run crawling (in millis)
   arg3: initial url to crawl
   All args are optional"
  [args]
  [(if (= 3 (count args)) (last args) "http://golang.org/ref/")
   (Integer/valueOf (or (first args) 1))
   (Integer/valueOf (or (second args) 2000))])

(defn -main
  "See parse-args fn for the optional args that can be passed in."
  [& args]
  (let [[url ncrawlers duration] (parse-args args)]
    (init url)
    (start ncrawlers)
    (Thread/sleep duration)
    (stop-crawlers ncrawlers)
    (-> (stop-frequency-reducer-and-get-result)
        report)
    (go/stop)))
