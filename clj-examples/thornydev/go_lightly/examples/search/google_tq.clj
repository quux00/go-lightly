(ns thornydev.go-lightly.examples.search.google
  (:require [thornydev.go-lightly.core :as go]))


(defn fake-search [kind]
  (fn [query]
    (Thread/sleep (rand-int 100))
    (format "%s result for '%s'\n" kind query)))

(def web   (fake-search "web"))
(def image (fake-search "image"))
(def video (fake-search "video"))

;; ---[ Google 1.0 Search ]--- ;;

(defn google-1 [query]
  (reduce #(conj % (%2 query)) [] [web image video]))

;; ---[ Google 2.0 Search ]--- ;;

;; each search transfers onto the same channel
(def google-20 [query]
  (let [ch (go/go-channel)]
    (doseq [search [web image video]]
      (go (.transfer ch (search query))))
    (vec
     (for [_ (range 3)] (.take ch)))))

;; TODO: document here
(defn google-21 [query]
  (let [ch (go/go-channel)
        timeout 80]  ;; TODO: where did this number come from?
    )
  )
