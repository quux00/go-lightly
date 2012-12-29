(defproject go-lightly "0.1.0-SNAPSHOT"
  :description "Replicating Go concurrency features in Clojure"
  :url "https://www.youtube.com/watch?v=f6kdp27TYZs&feature=youtu.be"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:dependencies [[criterium "0.3.1"]]}}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [lamina "0.5.0-beta8"]]
  :main thornydev.go-lightly.core)
