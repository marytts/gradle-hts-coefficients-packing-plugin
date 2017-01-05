package de.dfki.mary.htspacking

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Zip

import static groovyx.gpars.GParsPool.runForkJoin
import static groovyx.gpars.GParsPool.withPool

import de.dfki.mary.htspacking.*

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.xml.*

class HTSPackingPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply JavaPlugin
        project.plugins.apply MavenPlugin

        project.sourceCompatibility = JavaVersion.VERSION_1_7

        // Load configuration
        def slurper = new JsonSlurper()
        def config_file = project.rootProject.ext.config_file
        def config = slurper.parseText( config_file.text )

        // Adapt pathes
        DataFileFinder.project_path = new File(getClass().protectionDomain.codeSource.location.path).parent
        if (config.data.project_dir) {
            DataFileFinder.project_path = config.data.project_dir
        }

        def beams = config.settings.training.beam.split() as List
        def nb_proc_local = 1
        if (project.gradle.startParameter.getMaxWorkerCount() != 0) {
            nb_proc_local = Runtime.getRuntime().availableProcessors(); // By default the number of core
            if (config.settings.nb_proc) {
                if (config.settings.nb_proc > nb_proc_local) {
                    throw Exception("You will overload your machine, preventing stop !")
                }

                nb_proc_local = config.settings.nb_proc
            }
        }

        project.ext {
            maryttsVersion = '5.1.2'


            trained_files = new HashMap()


            // Nb processes
            nb_proc = nb_proc_local

            // HTS wrapper
            utils_dir = "$project.buildDir/tmp/utils"

            template_dir = "$project.buildDir/tmp/templates"


            input_file = DataFileFinder.getFilePath(config.data.wav_dir) + "/${project.name}.wav"
            basename = project.name
        }

        project.status = project.version.endsWith('SNAPSHOT') ? 'integration' : 'release'

        project.repositories {
            jcenter()
            maven {
                url 'http://oss.jfrog.org/artifactory/repo'
            }
        }

        project.configurations.create 'legacy'

        project.sourceSets {
            main {
                java {
                    // srcDir project.generatedSrcDir
                }
            }
            test {
                java {
                    // srcDir project.generatedTestSrcDir
                }
            }
        }

        project.task('prepareEnvironment') {
        }

        project.afterEvaluate {

            /**
             * CMP generation task
             */
            project.task('generateCMP') {
                dependsOn "prepareEnvironment"
                (new File("$project.buildDir/cmp")).mkdirs()
                outputs.files "$project.buildDir/cmp" + project.basename + ".cmp"


                doLast {

                    def extToDir = new Hashtable<String, String>()
                    extToDir.put("cmp".toString(), "$project.buildDir/cmp".toString())

                    project.user_configuration.models.cmp.streams.each  { stream ->
                        def kind = stream.kind
                        extToDir.put(kind.toLowerCase().toString(), stream.coeffDir.toString())
                    }

                    def extractor = new ExtractCMP(config_file.toString())
                    extractor.setDirectories(extToDir)
                    extractor.extract("$project.basename")
                }
            }

            /**
             * FFO generation task
             */
            project.task('generateFFO') {
                dependsOn "prepareEnvironment"
                (new File("$project.buildDir/ffo")).mkdirs()
                outputs.files "$project.buildDir/ffo" + project.basename + ".ffo"


                doLast {

                    def extToDir = new Hashtable<String, String>()
                    extToDir.put("ffo".toString(), "$project.buildDir/ffo".toString())

                    project.user_configuration.models.ffo.streams.each  { stream ->
                        def kind = stream.kind
                        extToDir.put(kind.toLowerCase().toString(), stream.coeffDir.toString())
                    }

                    def extractor = new ExtractFFO(config_file.toString())
                    extractor.setDirectories(extToDir)
                    extractor.extract("$project.basename")
                }
            }

            project.task('pack') {
                dependsOn "generateCMP"
                if (project.user_configuration.models.ffo) {
                    dependsOn "generateFFO"
                }
            }
        }
    }

}
