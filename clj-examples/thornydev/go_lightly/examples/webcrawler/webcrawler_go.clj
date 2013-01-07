(ns thornydev.go-lightly.examples.webcrawler.webcrawler-go
  (:require [thornydev.go-lightly.core :as go]
            [net.cgrand.enlive-html :as enlive])
  (:use [clojure.java.io :only (as-url)]
        [clojure.string :only (lower-case)])
  (:import (java.net URL MalformedURLException)))


;; want a buffered channel that can allow a large
;; number of urls to crawl
(def url-channel (go/channel Integer/MAX_VALUE))
;; result from parse the word frequencies from an
;; individual page
(def freqs-channel (go/channel Integer/MAX_VALUE))


;; TODO: not sure about these yet
(def crawled-urls (atom #{}))
(def word-freqs (atom {}))

(def agents (set
             (repeatedly 25 #(agent {:func #'get-url
                                     :channel url-channel}))))



(declare run process handle-results links-from words-from)

(defn get-url [channel]
  (let [url (as-url (.take channel))]
    
    )
  )


;; Go version:
;;  -read from url-channel
;;  -pause/unpause on wait-channel
;;  -as parse links from page put back in url-channel
;;   => need "unique" channel where can ensure only put onto channel once?
;;  -as parse words, keep local count, push counts onto wordfreq-channel
;;  -single go-routine proccess wordfreq-ch output updating the
;;    word-freqs atom (no contention on the atom, should be v. fast)
;;  


(defn crawl [wait-channel]
  ;; look at Pike's wait example => was it in a select?
  (loop [msg (go/select url-channel wait-channel)]
    
    )
  
  (loop [url (as-url (.take url-channel))]
    (process-url) ;; placeholder -> includes transferring urls
                  ;; to url-channel and 
    (when-not (nil? (.peek url-channel))
      (if (= :pause (.take )))
      )
    )
  )

(defn run []
  (go (crawl (go-channel))))




(defn- links-from
  [base-url html]
  (remove nil? (for [link (enlive/select html [:a])]
                 (when-let [href (-> link :attrs :href)]
                   (try
                     (URL. base-url href)
                     ;; ignore bad URLs
                     (catch MalformedURLException e))))))

(defn- words-from
  [html]
  (let [chunks (-> html
                   (enlive/at [:script] nil)
                   (enlive/select [:body enlive/text-node]))]
    (->> chunks
         (mapcat (partial re-seq #"\w+"))
         (remove (partial re-matches #"\d+"))
         (map lower-case))))
