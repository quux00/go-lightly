(defproject thornydev/go-lightly "0.3.2"
  :description "Clojure library to facilitate CSP concurrent programming based on Go concurrency constructs"
  :url "https://github.com/midpeter444/go-lightly"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:dependencies [[criterium "0.3.1"]]}}
  :dependencies [[org.clojure/clojure "1.5.0-RC4"]
                 [lamina "0.5.0-beta8"]
                 [enlive "1.0.1"]]
  :source-paths ["src" "clj-examples"]
  :javac-options ["-target" "1.7"]
  :jvm-opts ["-Xmx1g" "-server"] 
  :main thornydev.go-lightly.examples.run-examples)
