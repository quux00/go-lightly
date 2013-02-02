(ns thornydev.go-lightly.examples.agents.cycling)

;; --- [sync master controller version] --- ;;

(declare step2 step3)

(defn step1 [agt]
  {:task step2
   :result (conj (:result agt) :step1)})

(defn step2 [agt]
  {:task step3
   :result (conj (:result agt) :step2)})

(defn step3 [agt]
  {:task nil
   :result (conj (:result agt) :step3)})

(def fsm (agent {:task step1 :result []}))

(defn run-sync []
  (println @fsm)

  (send fsm (:task @fsm))
  (await-for 1000 fsm)
  (println @fsm)
  
  (send fsm (:task @fsm))
  (await-for 1000 fsm)
  (println @fsm)
  
  (send fsm (:task @fsm))
  (await-for 1000 fsm)
  (println @fsm))


;; --- [async inner-send version] --- ;;

(defn step3a [agtval]
  {:task nil
   :result (conj (:result agtval) :step3a)})

(defn step2a [agtval]
  (let [nuval {:task step3a
               :result (conj (:result agtval) :step2a)}]
    (send *agent* (:task nuval))
    nuval))

(defn step1a [agtval]
  (let [nuval {:task step2a
               :result (conj (:result agtval) :step1a)}]
    (send *agent* (:task nuval))
    nuval))

(def fsma (agent {:task step1a :result []}))

(defn run-async []
  (send fsma (:task @fsma))
  ;; this is a race condition, bcs it only waits for the first send to finish, not all the sends
  ;; could add a watch or CountDownLatch or use a messaging channel or ???
  (await-for 1000 fsma)
  (println @fsma))
