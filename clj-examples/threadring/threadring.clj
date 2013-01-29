;; The Computer Language Benchmarks Game
;; http://shootout.alioth.debian.org/
;;
;; contributed by Kenneth Jonsson

(ns threadring.threadring (:gen-class))

(defn pass-token [slot value]
  (if (zero? value)
    (do (println (:id slot))
        ;; (System/exit 0)
        )
    (do
      (send (:next slot) pass-token (dec value))
      slot)))

(defn pass-token-orig [ slot value ]
  (when (zero? value)
    (println (:id slot))
    (System/exit 0))
  (send (:next slot) pass-token (dec value))
  slot)

(defn create-ring-and-start [ _ ring-sz initial-value ]
  ;; "send" will be defered until the state of the current agent has
  ;; been set to the state returned by this function
  (send *agent* pass-token initial-value)
  ;; create a ring of "ring-sz" agents linked via ":next"
  { :id 1
   :next (reduce (fn [ next-slot id ]
                   (agent { :next next-slot :id id }))
                 *agent*
                 (range ring-sz 1 -1)) })

(defn -main [ & args ]
  (send (agent nil)
        create-ring-and-start
        503
        (if (empty? args) 1000 (Integer/parseInt (first args)))))
